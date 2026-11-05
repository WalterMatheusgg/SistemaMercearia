package br.com.ferragens;

import static org.assertj.core.api.Assertions.*;

import br.com.ferragens.auth.*;
import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.commerce.*;
import br.com.ferragens.shared.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;

import java.net.*;
import java.net.http.*;
import java.util.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccountsIntegrationTest {
    private static final String SCHEMA =
            "accounts_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        IntegrationServices.configure(p, SCHEMA);
    }

    @Autowired AccountsService accounts;
    @Autowired Db db;
    @Autowired PasswordEncoder encoder;
    @LocalServerPort int port;
    Actor owner;
    String username, password = "password-for-account-tests1";

    @BeforeEach
    void fixture() {
        UUID id = UUID.randomUUID();
        username = "owner-" + id;
        db.jdbc.update(
                "INSERT INTO app_user(id,store_id,username,password_hash,email) VALUES(?,?,?,?,?)",
                id,
                Db.STORE,
                username,
                encoder.encode(password),
                id + "@ferragens.local");
        db.jdbc.update("INSERT INTO user_role(user_id,role_code) VALUES(?,'ROLE_OWNER')", id);
        owner = new Actor(id, Db.STORE, username);
    }

    @Test
    void changedAccessInvalidatesExistingHttpSession() throws Exception {
        var client = client();
        login(client, username, password);
        UUID id =
                Db.id(
                        accounts.saveUser(
                                owner,
                                UUID.randomUUID(),
                                null,
                                new AccountsService.UserInput(
                                        "staff-" + UUID.randomUUID(),
                                        null,
                                        password,
                                        Set.of("SELLER"),
                                        true),
                                true),
                        "id");
        var user = db.one("SELECT username FROM app_user WHERE id=?", id);
        var staff = client();
        login(staff, Db.text(user, "username"), password);
        assertThat(get(staff, "/api/auth/me").statusCode()).isEqualTo(200);
        accounts.saveUser(
                owner,
                UUID.randomUUID(),
                id,
                new AccountsService.UserInput(
                        Db.text(user, "username"), null, null, Set.of("SELLER"), false),
                true);
        assertThat(get(staff, "/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void resetTokenIsSingleUseAndInvalidatesTheOldSession() throws Exception {
        var client = client();
        login(client, username, password);
        accounts.requestReset(username);
        String token = latestToken(owner.userId(), "reset-password");
        accounts.reset(new AccountsService.Reset(token, "a-new-long-password-123"));
        assertThatThrownBy(
                        () ->
                                accounts.reset(
                                        new AccountsService.Reset(
                                                token, "another-long-password-123")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(get(client, "/api/auth/me").statusCode()).isEqualTo(401);
        String hash =
                Db.text(
                        db.one("SELECT password_hash FROM app_user WHERE id=?", owner.userId()),
                        "passwordHash");
        assertThat(encoder.matches(password, hash)).isFalse();
        assertThat(encoder.matches("a-new-long-password-123", hash)).isTrue();
    }

    @Test
    void verificationMustSucceedBeforeCustomerCheckout() {
        String user = "customer-" + UUID.randomUUID();
        accounts.register(
                new AccountsService.Register(
                        user, user + "@ferragens.local", password, "Cliente", null));
        UUID id = Db.id(db.one("SELECT id FROM app_user WHERE username=?", user), "id");
        assertThat(
                        db.one("SELECT email_verified FROM app_user WHERE id=?", id)
                                .get("emailVerified"))
                .isEqualTo(false);
        String token = latestToken(id, "verify-email");
        accounts.verify(token);
        assertThat(
                        db.one("SELECT email_verified FROM app_user WHERE id=?", id)
                                .get("emailVerified"))
                .isEqualTo(true);
        assertThatThrownBy(() -> accounts.verify(token)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void managerCannotGrantOwnerAndCurrentOwnerCannotRemoveOwnAccess() {
        assertThatThrownBy(
                        () ->
                                accounts.saveUser(
                                        owner,
                                        UUID.randomUUID(),
                                        null,
                                        new AccountsService.UserInput(
                                                "other-" + UUID.randomUUID(),
                                                null,
                                                password,
                                                Set.of("OWNER"),
                                                true),
                                        false))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(
                        () ->
                                accounts.saveUser(
                                        owner,
                                        UUID.randomUUID(),
                                        owner.userId(),
                                        new AccountsService.UserInput(
                                                username, null, null, Set.of("SELLER"), true),
                                        true))
                .isInstanceOf(BusinessRuleException.class);
    }

    private String latestToken(UUID user, String route) {
        String body =
                Db.text(
                        db.one(
                                "SELECT body FROM notification_outbox WHERE user_id=? AND body LIKE"
                                        + " ? ORDER BY created_at DESC LIMIT 1",
                                user,
                                "%/" + route + "?token=%"),
                        "body");
        return body.substring(body.indexOf("?token=") + 7).split("\\s")[0];
    }

    private HttpClient client() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void login(HttpClient client, String user, String password) throws Exception {
        get(client, "/api/auth/csrf");
        var cookies = (CookieManager) client.cookieHandler().orElseThrow();
        String csrf =
                cookies.getCookieStore().getCookies().stream()
                        .filter(c -> c.getName().equals("XSRF-TOKEN"))
                        .findFirst()
                        .orElseThrow()
                        .getValue();
        var response =
                client.send(
                        HttpRequest.newBuilder(
                                        URI.create("http://localhost:" + port + "/api/auth/login"))
                                .header("Content-Type", "application/json")
                                .header("X-XSRF-TOKEN", csrf)
                                .POST(
                                        HttpRequest.BodyPublishers.ofString(
                                                "{\"username\":\""
                                                        + user
                                                        + "\",\"password\":\""
                                                        + password
                                                        + "\"}"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    }
}
