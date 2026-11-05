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
class PlatformIntegrationTest {

    private static final String SCHEMA = "platform_" + java.util.UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void serviceProperties(DynamicPropertyRegistry properties) {
        IntegrationServices.configure(properties, SCHEMA);
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
        // O schema é exclusivo e aleatório para esta classe de integração.
        jdbc.execute("TRUNCATE inventory_movement, inventory_balance, product, user_role, app_user CASCADE");
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
        String anonymousCsrf = ownerCsrf;
        HttpResponse<String> login = send(owner, "POST", "/api/auth/login",
                "{\"username\":\"owner-it\",\"password\":\"owner-password\"}", ownerCsrf);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().allValues("Set-Cookie").stream().anyMatch(cookie ->
            cookie.contains("HttpOnly") && cookie.contains("SameSite=Lax"))).isTrue();
        ownerCsrf = csrf(owner);
        assertThat(ownerCsrf).isNotEqualTo(anonymousCsrf);
        assertThat(send(owner, "POST", "/api/admin/products", "{}", anonymousCsrf).statusCode()).isEqualTo(403);

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
        HttpResponse<String> internalCatalog = send(owner,
            "GET", "/api/admin/catalog?query=Furadeira", null, null);
        assertThat(internalCatalog.statusCode()).as(internalCatalog.body()).isEqualTo(200);
        assertThat(internalCatalog.body()).contains("Furadeira");
        assertThat(send(anonymous, "GET", "/api/admin/catalog", null, null).statusCode()).isIn(401, 403);
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
        String csrfBeforeLogout = ownerCsrf;
        assertThat(send(owner, "POST", "/api/auth/logout", "{}", ownerCsrf).statusCode()).isEqualTo(204);
        assertThat(send(owner, "GET", "/api/auth/me", null, null).statusCode()).isIn(401, 403);
        assertThat(csrf(owner)).isNotEqualTo(csrfBeforeLogout);
        assertThat(users.findByUsername("owner-it")).isPresent();
    }

    @Test
    void createsProductWithoutPublicCodeAndCanReactivateIt() throws Exception {
        HttpClient owner = client();
        String csrf = csrf(owner);
        assertThat(send(owner, "POST", "/api/auth/login",
                "{\"username\":\"owner-it\",\"password\":\"owner-password\"}", csrf).statusCode()).isEqualTo(200);
        csrf = csrf(owner);
        String body = "{\"name\":\"Bucha sem código\",\"description\":null,"
                + "\"categoryId\":\"00000000-0000-0000-0000-000000000104\",\"baseUnitCode\":\"UN\","
                + "\"cost\":\"0.00\",\"price\":\"0.00\"}";
        var created = send(owner, "POST", "/api/admin/products", body, csrf);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(created.body()).contains("INT-", "Bucha sem código");
        String id = extractJsonString(created.body(), "id");
        var updated = send(owner, "PUT", "/api/admin/products/" + id,
            body.replace("Bucha sem código", "Bucha 8 mm"), csrf);
        assertThat(updated.statusCode()).as(updated.body()).isEqualTo(200);
        assertThat(updated.body()).contains("Bucha 8 mm");
        assertThat(send(owner, "DELETE", "/api/admin/products/" + id, null, csrf).statusCode()).isEqualTo(204);
        var restored = send(owner, "POST", "/api/admin/products/" + id + "/reactivate", "{}", csrf);
        assertThat(restored.statusCode()).as(restored.body()).isEqualTo(200);
        assertThat(restored.body()).contains("\"active\":true");
    }

    @Test
    void protectsApiDocumentation() throws Exception {
        assertThat(send(client(), "GET", "/v3/api-docs", null, null).statusCode()).isEqualTo(401);
        HttpClient owner = client();
        String token = csrf(owner);
        assertThat(send(owner, "POST", "/api/auth/login",
                "{\"username\":\"owner-it\",\"password\":\"owner-password\"}", token).statusCode()).isEqualTo(200);
        assertThat(send(owner, "GET", "/v3/api-docs", null, null).statusCode()).isEqualTo(200);
    }

    @Test
    void inventoryApiEnforcesRolesIdempotencyAndProductMeasurement() throws Exception {
        HttpClient owner = client();
        String token = csrf(owner);
        assertThat(send(owner, "POST", "/api/auth/login",
                "{\"username\":\"owner-it\",\"password\":\"owner-password\"}", token).statusCode()).isEqualTo(200);
        token = csrf(owner);
        String body = "{\"code\":\"INV-01\",\"name\":\"Parafuso\",\"categoryId\":\"00000000-0000-0000-0000-000000000104\","
                + "\"baseUnitCode\":\"UN\",\"cost\":\"1.00\",\"price\":\"2.00\"}";
        var created = send(owner, "POST", "/api/admin/products", body, token);
        assertThat(created.statusCode()).isEqualTo(201);
        String id = extractJsonString(created.body(), "id");
        String location = "00000000-0000-0000-0000-000000000201";
        String movement = "{\"productId\":\"" + id + "\",\"locationId\":\"" + location
                + "\",\"kind\":\"INITIAL\",\"quantity\":\"50\",\"reason\":\"Saldo conferido\"}";
        String key = java.util.UUID.randomUUID().toString();
        var first = sendMovement(owner, movement, token, key);
        assertThat(first.statusCode()).as(first.body()).isEqualTo(200);
        var retry = sendMovement(owner, movement, token, key);
        assertThat(extractJsonString(retry.body(), "id")).isEqualTo(extractJsonString(first.body(), "id"));
        assertThat(send(owner, "POST", "/api/admin/inventory/movements", movement, token).statusCode()).isEqualTo(400);
        assertThat(send(owner, "PUT", "/api/admin/products/" + id, body.replace("\"UN\"", "\"M\""), token).statusCode()).isEqualTo(409);

        HttpClient seller = client(); String sellerToken = csrf(seller);
        assertThat(send(seller, "POST", "/api/auth/login", "{\"username\":\"seller-it\",\"password\":\"seller-password\"}", sellerToken).statusCode()).isEqualTo(200);
        sellerToken = csrf(seller);
        assertThat(send(seller, "GET", "/api/admin/inventory/balances?locationId=" + location, null, null).statusCode()).isEqualTo(200);
        assertThat(sendMovement(seller, movement, sellerToken, java.util.UUID.randomUUID().toString()).statusCode()).isEqualTo(403);

        saveUser("stock-it", "stock-password", "ROLE_STOCKKEEPER");
        HttpClient stock = client(); String stockToken = csrf(stock);
        assertThat(send(stock, "POST", "/api/auth/login", "{\"username\":\"stock-it\",\"password\":\"stock-password\"}", stockToken).statusCode()).isEqualTo(200);
        stockToken = csrf(stock);
        assertThat(sendMovement(stock, movement.replace("INITIAL", "COUNT"), stockToken, java.util.UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
    }

    @Test
    void sendsSmokeEmailToMailpitWithoutExternalRelay() throws Exception {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("sistema@ferragens.local");
        message.setTo("operacao@ferragens.local");
        message.setSubject("ferragens-smoke-test");
        message.setText("Mensagem local de verificação do Spring Mail.");
        mailSender.send(message);

        URI messagesUri = URI.create(IntegrationServices.mailpitUrl() + "/api/v1/messages");
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

    private HttpResponse<String> sendMovement(HttpClient client, String body, String token, String key) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/admin/inventory/movements"))
                .header("Content-Type", "application/json").header("X-XSRF-TOKEN", token).header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString());
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
