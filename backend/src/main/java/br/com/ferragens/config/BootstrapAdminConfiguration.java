package br.com.ferragens.config;

import br.com.ferragens.auth.RoleRepository;
import br.com.ferragens.auth.UserAccount;
import br.com.ferragens.auth.UserAccountRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@Profile({"local", "bootstrap"})
public class BootstrapAdminConfiguration {

    @Bean
    CommandLineRunner bootstrapAdmin(
            UserAccountRepository users,
            RoleRepository roles,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.enabled:true}") boolean enabled,
            @Value("${app.bootstrap.admin.username:}") String username,
            @Value("${app.bootstrap.admin.password:}") String password,
            @Value("${app.bootstrap.admin.email:}") String email) {
        return args -> {
            if (!enabled) return;
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                jdbc.execute("SELECT pg_advisory_xact_lock(72674273370918455)");
                if (Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "SELECT EXISTS(SELECT 1 FROM app_user u JOIN user_role r ON"
                                            + " r.user_id=u.id WHERE u.enabled AND"
                                            + " r.role_code='ROLE_OWNER')",
                                    Boolean.class))) {
                return;
            }
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                throw new IllegalStateException(
                        "Defina BOOTSTRAP_ADMIN_USERNAME e BOOTSTRAP_ADMIN_PASSWORD no .env"
                                + " local.");
            }
            if (password.length() < 4
                    || !password.matches("(?s).*[0-9].*")
                    || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalStateException(
                        "A senha deve conter pelo menos 4 caracteres e 1 número (máximo 72 bytes).");
            var owner =
                    roles.findById("ROLE_OWNER")
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "A migração não criou o papel ROLE_OWNER."));
            var account = users.saveAndFlush(
                    new UserAccount(
                            username.trim(),
                            passwordEncoder.encode(password),
                            java.util.Set.of(owner)));
            if (StringUtils.hasText(email)) {
                String address = email.trim().toLowerCase(java.util.Locale.ROOT);
                if (!address.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                    throw new IllegalStateException("E-mail de administrador inválido.");
                }
                jdbc.update("UPDATE app_user SET email=?,email_verified=false WHERE id=?",
                        address, account.getId());
                jdbc.update("INSERT INTO notification_preference(user_id,sale_completed,stock_low,daily_summary)"
                        + " VALUES(?,true,true,true) ON CONFLICT(user_id) DO NOTHING", account.getId());
            }
            });
        };
    }
}
