package br.com.ferragens;

import br.com.ferragens.auth.Role;
import br.com.ferragens.auth.RoleRepository;
import br.com.ferragens.auth.UserAccount;
import br.com.ferragens.auth.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class PlatformIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.6-alpine"));

    @Container
    static final GenericContainer<?> mailpit = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27.11"))
            .withExposedPorts(1025, 8025);

    @DynamicPropertySource
    static void serviceProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.mail.host", mailpit::getHost);
        properties.add("spring.mail.port", () -> mailpit.getMappedPort(1025));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserAccountRepository users;

    @Autowired
    private RoleRepository roles;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createPersistedUsers() {
        jdbc.update("delete from product");
        jdbc.update("delete from user_role");
        jdbc.update("delete from app_user");
        saveUser("owner-it", "owner-password", "ROLE_OWNER");
        saveUser("seller-it", "seller-password", "ROLE_SELLER");
    }

    @Test
    void protectsAndPersistsProductWorkflowWithSessionCsrfAndRoles() throws Exception {
        HttpClient anonymous = client();
        HttpResponse<String> blocked = send(anonymous, "GET", "/api/admin/products", null, null);
        assertThat(blocked.statusCode()).isIn(401, 403);
        assertThat(blocked.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");

        HttpClient owner = client();
        String ownerCsrf = csrf(owner);
        HttpResponse<String> login = send(owner, "POST", "/api/auth/login",
                "{\"username\":\"owner-it\",\"password\":\"owner-password\"}", ownerCsrf);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().allValues("Set-Cookie").stream().anyMatch(cookie ->
            cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax"))).isTrue();
        ownerCsrf = csrf(owner);

        HttpResponse<String> invalid = send(owner, "POST", "/api/admin/products",
            "{\"code\":\"\",\"name\":\"\",\"baseUnitCode\":\"\",\"cost\":-1,\"price\":-1}", ownerCsrf);
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");

        String productBody = "{\"code\":\"fur-001\",\"name\":\"Furadeira\",\"description\":\"13 mm\","
                + "\"categoryId\":\"00000000-0000-0000-0000-000000000101\",\"baseUnitCode\":\"UN\","
                + "\"cost\":\"120.00\",\"price\":\"199.90\"}";
            assertThat(send(owner, "POST", "/api/admin/products", productBody, null).statusCode()).isEqualTo(403);
        HttpResponse<String> created = send(owner, "POST", "/api/admin/products", productBody, ownerCsrf);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("FUR-001", "\"price\":\"199.90\"", "\"cost\":\"120.00\"");
        String id = extractJsonString(created.body(), "id");

        String secondProduct = "{\"code\":\"CAB-001\",\"name\":\"Cabo elétrico\",\"description\":null,"
            + "\"categoryId\":\"00000000-0000-0000-0000-000000000102\",\"baseUnitCode\":\"M\","
            + "\"cost\":\"4.25\",\"price\":\"7.50\"}";
        assertThat(send(owner, "POST", "/api/admin/products", secondProduct, ownerCsrf).statusCode()).isEqualTo(201);

        HttpResponse<String> listing = send(owner, "GET", "/api/admin/products?page=0&size=10", null, null);
        assertThat(listing.statusCode()).as(listing.body()).isEqualTo(200);
        assertThat(listing.body()).contains("FUR-001", "Furadeira", "CAB-001");
        HttpResponse<String> emptyFilter = send(owner,
            "GET", "/api/admin/products?page=0&size=10&query=", null, null);
        assertThat(emptyFilter.statusCode()).as(emptyFilter.body()).isEqualTo(200);
        assertThat(emptyFilter.body()).contains("\"totalElements\":2");
        HttpResponse<String> filledFilter = send(owner,
            "GET", "/api/admin/products?page=0&size=10&query=Furadeira", null, null);
        assertThat(filledFilter.statusCode()).as(filledFilter.body()).isEqualTo(200);
        assertThat(filledFilter.body()).contains("FUR-001", "\"totalElements\":1");
        HttpResponse<String> pageOne = send(owner,
            "GET", "/api/admin/products?page=1&size=1", null, null);
        assertThat(pageOne.statusCode()).as(pageOne.body()).isEqualTo(200);
        assertThat(pageOne.body()).contains("\"totalElements\":2", "\"page\":1", "\"size\":1");

        String updatedProduct = "{\"code\":\"fur-001\",\"name\":\"Furadeira 13 mm\",\"description\":\"Com impacto\","
            + "\"categoryId\":\"00000000-0000-0000-0000-000000000101\",\"baseUnitCode\":\"UN\","
            + "\"cost\":\"125.00\",\"price\":\"209.90\"}";
        HttpResponse<String> updated = send(owner, "PUT", "/api/admin/products/" + id, updatedProduct, ownerCsrf);
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
        assertThat(updated.body()).contains("Furadeira 13 mm", "\"price\":\"209.90\"");

        HttpClient seller = client();
        String sellerCsrf = csrf(seller);
        assertThat(send(seller, "POST", "/api/auth/login",
                "{\"username\":\"seller-it\",\"password\":\"seller-password\"}", sellerCsrf).statusCode())
                .isEqualTo(200);
        sellerCsrf = csrf(seller);
        assertThat(send(seller, "POST", "/api/admin/products", productBody, sellerCsrf).statusCode()).isEqualTo(403);
        HttpResponse<String> forbidden = send(seller, "POST", "/api/admin/products", productBody, sellerCsrf);
        assertThat(forbidden.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");

        ownerCsrf = csrf(owner);
        assertThat(send(owner, "DELETE", "/api/admin/products/" + id, null, ownerCsrf).statusCode()).isEqualTo(204);
        HttpResponse<String> deactivated = send(owner, "GET", "/api/admin/products/" + id, null, null);
        assertThat(deactivated.statusCode()).isEqualTo(200);
        assertThat(deactivated.body()).contains("\"active\":false");

        ownerCsrf = csrf(owner);
        assertThat(send(owner, "POST", "/api/auth/logout", "{}", ownerCsrf).statusCode()).isEqualTo(204);
        assertThat(send(owner, "GET", "/api/auth/me", null, null).statusCode()).isIn(401, 403);
        assertThat(users.findByUsername("owner-it")).isPresent();
    }

    @Test
    void sendsSmokeEmailToMailpitWithoutExternalRelay() throws Exception {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("sistema@ferragens.local");
        message.setTo("operacao@ferragens.local");
        message.setSubject("ferragens-smoke-test");
        message.setText("Mensagem local de verificação do Spring Mail.");
        mailSender.send(message);

        URI messagesUri = URI.create("http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025)
                + "/api/v1/messages");
        HttpResponse<String> messages = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(messagesUri).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(messages.statusCode()).isEqualTo(200);
        assertThat(messages.body()).contains("ferragens-smoke-test", "operacao@ferragens.local");
    }

    private void saveUser(String username, String password, String roleCode) {
        Role role = roles.findById(roleCode).orElseThrow();
        users.save(new UserAccount(username, passwordEncoder.encode(password), Set.of(role)));
    }

    private HttpClient client() {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        return HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    private String csrf(HttpClient client) throws Exception {
        HttpResponse<String> response = send(client, "GET", "/api/auth/csrf", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        CookieManager cookies = (CookieManager) client.cookieHandler().orElseThrow();
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                .map(java.net.HttpCookie::getValue)
                .findFirst().orElseThrow();
    }

    private HttpResponse<String> send(HttpClient client, String method, String path,
                                      String body, String csrf) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        if (csrf != null) request.header("X-XSRF-TOKEN", csrf);
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String extractJsonString(String body, String key) {
        String marker = "\"" + key + "\":\"";
        int start = body.indexOf(marker);
        int valueStart = start + marker.length();
        return body.substring(valueStart, body.indexOf('"', valueStart));
    }
}