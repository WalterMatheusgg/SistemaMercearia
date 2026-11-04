package br.com.ferragens.auth;

import br.com.ferragens.auth.CurrentActorService.Actor;
import br.com.ferragens.commerce.*;
import br.com.ferragens.notification.Notifications;
import br.com.ferragens.shared.*;

import jakarta.validation.constraints.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class AccountsService {
    private final Db db;
    private final CommandGate gate;
    private final PasswordEncoder passwords;
    private final Notifications notices;
    private final String publicUrl;
    private final SecureRandom random = new SecureRandom();

    public AccountsService(
            Db db,
            CommandGate gate,
            PasswordEncoder passwords,
            Notifications notices,
            @Value("${app.public-url:http://localhost:4200}") String publicUrl) {
        this.db = db;
        this.gate = gate;
        this.passwords = passwords;
        this.notices = notices;
        this.publicUrl = publicUrl.replaceAll("/$", "");
    }

    public record Register(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._@-]{3,120}") String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotBlank @Size(max = 160) String name,
            @Size(max = 40) String phone) {}

    public record UserInput(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9._@-]{3,120}") String username,
            @Email @Size(max = 254) String email,
            @Size(min = 12, max = 72) String password,
            @NotEmpty Set<String> roles,
            boolean enabled) {}

    public record Reset(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 12, max = 72) String password) {}

    public record Preferences(boolean saleCompleted, boolean stockLow, boolean dailySummary) {}

    public record Profile(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 40) String phone,
            @Size(max = 500) String address) {}

    public Map<String, Object> register(Register body) {
        if (!Boolean.TRUE.equals(
                db.jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM app_user u JOIN user_role r ON r.user_id=u.id"
                                + " WHERE u.enabled AND r.role_code='ROLE_OWNER')",
                        Boolean.class)))
            throw new BusinessRuleException(
                    "Loja ainda em configuração. Tente novamente mais tarde.");
        String email = email(body.email());
        String hash = passwords.encode(password(body.password()));
        UUID id = UUID.randomUUID();
        db.jdbc.update(
                "INSERT INTO app_user(id,store_id,username,password_hash,email) VALUES(?,?,?,?,?)",
                id,
                Db.STORE,
                body.username(),
                hash,
                email);
        db.jdbc.update("INSERT INTO user_role(user_id,role_code) VALUES(?,'ROLE_CUSTOMER')", id);
        db.jdbc.update(
                "INSERT INTO party(id,store_id,kind,name,email,phone,user_id)"
                        + " VALUES(?,?,'CUSTOMER',?,?,?,?)",
                UUID.randomUUID(),
                Db.STORE,
                body.name().trim(),
                email,
                Db.optional(body.phone(), 40),
                id);
        Actor actor = new Actor(id, Db.STORE, body.username());
        db.audit(actor, "CUSTOMER_REGISTERED", id, "Conta de cliente criada");
        issueToken(id, "VERIFY");
        return Map.of("username", body.username());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> users(Actor actor) {
        var result =
                db.rows(
                        "SELECT id,username,email,email_verified,enabled,created_at FROM app_user"
                                + " WHERE store_id=? ORDER BY username LIMIT 200",
                        actor.storeId());
        for (var user : result)
            user.put(
                    "roles",
                    db.jdbc.queryForList(
                            "SELECT substring(role_code,6) FROM user_role WHERE user_id=? ORDER BY"
                                    + " role_code",
                            String.class,
                            Db.id(user, "id")));
        return result;
    }

    public Map<String, Object> saveUser(
            Actor actor, UUID key, UUID id, UserInput body, boolean owner) {
        UUID result =
                gate.run(
                        actor,
                        key,
                        "user:save:" + id,
                        Arrays.asList(
                                body.username(),
                                body.email(),
                                body.roles(),
                                body.enabled(),
                                body.password() != null && !body.password().isBlank()),
                        () -> {
                            Set<String> allowed =
                                    Set.of(
                                            "OWNER",
                                            "MANAGER",
                                            "SELLER",
                                            "CASHIER",
                                            "STOCKKEEPER",
                                            "FINANCE",
                                            "CUSTOMER");
                            if (body.roles() == null
                                    || body.roles().isEmpty()
                                    || !allowed.containsAll(body.roles())
                                    || body.roles().contains("CUSTOMER")
                                            && body.roles().size() != 1)
                                throw new BusinessRuleException(
                                        "Papéis inválidos. Conta de cliente deve ter somente o"
                                                + " papel Cliente.");
                            // Owner and manager use this endpoint, but only an owner may change
                            // owner/manager accounts.
                            db.one("SELECT id FROM store WHERE id=? FOR UPDATE", actor.storeId());
                            UUID target = id == null ? UUID.randomUUID() : id;
                            Map<String, Object> previous =
                                    id == null
                                            ? null
                                            : db.one(
                                                    "SELECT * FROM app_user WHERE id=? AND"
                                                            + " store_id=? FOR UPDATE",
                                                    id,
                                                    actor.storeId());
                            var oldRoles =
                                    id == null
                                            ? List.<String>of()
                                            : db.jdbc.queryForList(
                                                    "SELECT substring(role_code,6) FROM user_role"
                                                            + " WHERE user_id=?",
                                                    String.class,
                                                    id);
                            if (!owner
                                    && (body.roles().stream()
                                                    .anyMatch(
                                                            r ->
                                                                    Set.of("OWNER", "MANAGER")
                                                                            .contains(r))
                                            || oldRoles.stream()
                                                    .anyMatch(
                                                            r ->
                                                                    Set.of("OWNER", "MANAGER")
                                                                            .contains(r))))
                                throw new org.springframework.security.access.AccessDeniedException(
                                        "Somente o dono pode administrar donos e gerentes.");
                            if (id != null
                                    && id.equals(actor.userId())
                                    && (!body.enabled()
                                            || !new HashSet<>(oldRoles).equals(body.roles())))
                                throw new BusinessRuleException(
                                        "Não remova seu próprio acesso nesta tela.");
                            if (oldRoles.contains("OWNER")
                                    && (!body.enabled() || !body.roles().contains("OWNER"))) {
                                long owners =
                                        db.jdbc.queryForObject(
                                                "SELECT count(*) FROM app_user u JOIN user_role r"
                                                    + " ON r.user_id=u.id WHERE u.store_id=? AND"
                                                    + " u.enabled AND r.role_code='ROLE_OWNER' AND"
                                                    + " u.id<>?",
                                                Long.class,
                                                actor.storeId(),
                                                id);
                                if (owners == 0)
                                    throw new ConflictException(
                                            "Mantenha pelo menos um dono ativo.");
                            }
                            String email =
                                    body.email() == null || body.email().isBlank()
                                            ? null
                                            : email(body.email());
                            if (id == null) {
                                db.jdbc.update(
                                        "INSERT INTO"
                                            + " app_user(id,store_id,username,password_hash,email,enabled)"
                                            + " VALUES(?,?,?,?,?,?)",
                                        target,
                                        actor.storeId(),
                                        body.username(),
                                        passwords.encode(password(body.password())),
                                        email,
                                        body.enabled());
                            } else {
                                boolean emailChanged =
                                        !Objects.equals(previous.get("email"), email);
                                db.jdbc.update(
                                        "UPDATE app_user SET username=?,email=?,email_verified=CASE"
                                            + " WHEN ? THEN false ELSE email_verified"
                                            + " END,enabled=?,auth_revision=auth_revision+1 WHERE"
                                            + " id=?",
                                        body.username(),
                                        email,
                                        emailChanged,
                                        body.enabled(),
                                        id);
                                if (body.password() != null && !body.password().isBlank())
                                    db.jdbc.update(
                                            "UPDATE app_user SET password_hash=? WHERE id=?",
                                            passwords.encode(password(body.password())),
                                            id);
                                db.jdbc.update("DELETE FROM user_role WHERE user_id=?", id);
                                if (emailChanged)
                                    db.jdbc.update(
                                            "UPDATE account_token SET used_at=now() WHERE user_id=?"
                                                    + " AND used_at IS NULL",
                                            id);
                            }
                            for (String role : body.roles())
                                db.jdbc.update(
                                        "INSERT INTO user_role(user_id,role_code) VALUES(?,?)",
                                        target,
                                        "ROLE_" + role);
                            if (body.roles().contains("CUSTOMER")
                                    && db.rows("SELECT id FROM party WHERE user_id=?", target)
                                            .isEmpty())
                                db.jdbc.update(
                                        "INSERT INTO party(id,store_id,kind,name,email,user_id)"
                                                + " VALUES(?,?,'CUSTOMER',?,?,?)",
                                        UUID.randomUUID(),
                                        actor.storeId(),
                                        body.username(),
                                        email,
                                        target);
                            db.audit(
                                    actor,
                                    "USER_UPDATED",
                                    target,
                                    "Acesso atualizado para "
                                            + body.username()
                                            + "; sessões anteriores invalidadas");
                            return target;
                        });
        return db.one("SELECT id,username,email,enabled FROM app_user WHERE id=?", result);
    }

    public void requestReset(String username) {
        var users =
                db.rows(
                        "SELECT id FROM app_user WHERE (username=? OR lower(email)=lower(?)) AND"
                                + " enabled",
                        username,
                        username);
        if (!users.isEmpty()) issueToken(Db.id(users.getFirst(), "id"), "RESET");
    }

    public void requestVerification(Actor actor) {
        issueToken(actor.userId(), "VERIFY");
    }

    private void issueToken(UUID id, String purpose) {
        var user =
                db.one("SELECT store_id,email FROM app_user WHERE id=? AND enabled FOR UPDATE", id);
        String email = Db.text(user, "email");
        if (email.isBlank()) return;
        boolean recent =
                Boolean.TRUE.equals(
                        db.jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM account_token WHERE user_id=? AND"
                                        + " purpose=? AND created_at>now()-interval '1 minute')",
                                Boolean.class,
                                id,
                                purpose));
        if (recent) return;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.jdbc.update(
                "UPDATE account_token SET used_at=now() WHERE user_id=? AND purpose=? AND used_at"
                        + " IS NULL",
                id,
                purpose);
        db.jdbc.update(
                "INSERT INTO account_token(token_hash,user_id,purpose,expires_at)"
                        + " VALUES(?,?,?,now()+interval '30 minutes')",
                CommandGate.hash(token),
                id,
                purpose);
        String route = purpose.equals("RESET") ? "reset-password" : "verify-email";
        notices.transactional(
                Db.id(user, "storeId"),
                id,
                email,
                "token:" + CommandGate.hash(token),
                purpose.equals("RESET") ? "Recuperar acesso" : "Verificar e-mail",
                (purpose.equals("RESET") ? "Redefina sua senha" : "Confirme seu e-mail")
                        + " pelo link abaixo, válido por 30 minutos e uma única utilização:\n"
                        + publicUrl
                        + "/"
                        + route
                        + "?token="
                        + token
                        + "\nSe você não solicitou, ignore esta mensagem.");
    }

    public void reset(Reset body) {
        var token = token(body.token(), "RESET");
        UUID user = Db.id(token, "userId");
        db.jdbc.update(
                "UPDATE app_user SET password_hash=?,auth_revision=auth_revision+1 WHERE id=?",
                passwords.encode(password(body.password())),
                user);
        db.jdbc.update(
                "UPDATE account_token SET used_at=now() WHERE user_id=? AND purpose='RESET' AND"
                        + " used_at IS NULL",
                user);
    }

    public void verify(String raw) {
        var token = token(raw, "VERIFY");
        db.jdbc.update(
                "UPDATE app_user SET email_verified=true WHERE id=?", Db.id(token, "userId"));
        db.jdbc.update(
                "UPDATE account_token SET used_at=now() WHERE token_hash=?", CommandGate.hash(raw));
    }

    private Map<String, Object> token(String raw, String purpose) {
        var candidate =
                db
                        .rows(
                                "SELECT user_id FROM account_token WHERE token_hash=? AND"
                                    + " purpose=?",
                                CommandGate.hash(raw),
                                purpose)
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new BusinessRuleException("Link inválido ou vencido."));
        db.one("SELECT id FROM app_user WHERE id=? FOR UPDATE", Db.id(candidate, "userId"));
        return db
                .rows(
                        "SELECT user_id FROM account_token WHERE token_hash=? AND purpose=? AND"
                                + " used_at IS NULL AND expires_at>now() FOR UPDATE",
                        CommandGate.hash(raw),
                        purpose)
                .stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new BusinessRuleException(
                                        "Link inválido, vencido ou já utilizado. Solicite outro."));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> preferences(Actor actor) {
        var user = db.one("SELECT email,email_verified FROM app_user WHERE id=?", actor.userId());
        var rows =
                db.rows(
                        "SELECT sale_completed,stock_low,daily_summary FROM notification_preference"
                                + " WHERE user_id=?",
                        actor.userId());
        user.putAll(
                rows.isEmpty()
                        ? Map.of("saleCompleted", false, "stockLow", false, "dailySummary", false)
                        : rows.getFirst());
        user.put(
                "history",
                db.rows(
                        "SELECT id,event_type,subject,status,attempts,created_at,sent_at,last_error"
                            + " FROM notification_outbox WHERE user_id=? ORDER BY created_at DESC"
                            + " LIMIT 50",
                        actor.userId()));
        return user;
    }

    public Map<String, Object> preferences(Actor actor, Preferences body, boolean management) {
        if (!management && (body.saleCompleted() || body.dailySummary()))
            throw new org.springframework.security.access.AccessDeniedException(
                    "Alertas de vendas e resumo exigem gerência.");
        db.jdbc.update(
                "INSERT INTO"
                    + " notification_preference(user_id,sale_completed,stock_low,daily_summary)"
                    + " VALUES(?,?,?,?) ON CONFLICT(user_id) DO UPDATE SET"
                    + " sale_completed=excluded.sale_completed,stock_low=excluded.stock_low,daily_summary=excluded.daily_summary",
                actor.userId(),
                body.saleCompleted(),
                body.stockLow(),
                body.dailySummary());
        db.audit(
                actor,
                "NOTIFICATION_PREFERENCES",
                actor.userId(),
                "Preferências de alerta alteradas");
        return preferences(actor);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> profile(Actor actor) {
        return db.one(
                "SELECT p.id,p.name,p.phone,p.address,u.email,u.email_verified FROM party p JOIN"
                        + " app_user u ON u.id=p.user_id WHERE p.user_id=? AND p.store_id=?",
                actor.userId(),
                actor.storeId());
    }

    public Map<String, Object> profile(Actor actor, Profile body) {
        db.one(
                "SELECT id FROM party WHERE user_id=? AND store_id=?",
                actor.userId(),
                actor.storeId());
        db.jdbc.update(
                "UPDATE party SET name=?,phone=?,address=?,updated_at=now() WHERE user_id=?",
                Db.required(body.name(), 160, "o nome"),
                Db.optional(body.phone(), 40),
                Db.optional(body.address(), 500),
                actor.userId());
        return profile(actor);
    }

    private String password(String value) {
        if (value == null
                || value.length() < 12
                || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new BusinessRuleException(
                    "Use senha com ao menos 12 caracteres e no máximo 72 bytes.");
        return value;
    }

    private String email(String value) {
        return Db.required(value, 254, "o e-mail").toLowerCase(Locale.ROOT);
    }
}
