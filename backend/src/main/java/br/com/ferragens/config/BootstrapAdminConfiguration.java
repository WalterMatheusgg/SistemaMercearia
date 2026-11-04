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

@Configuration
@Profile({"local", "bootstrap"})
public class BootstrapAdminConfiguration {

    @Bean
    CommandLineRunner bootstrapAdmin(
            UserAccountRepository users,
            RoleRepository roles,
            org.springframework.jdbc.core.JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.enabled:true}") boolean enabled,
            @Value("${app.bootstrap.admin.username:}") String username,
            @Value("${app.bootstrap.admin.password:}") String password) {
        return args -> {
            if (!enabled
                    || Boolean.TRUE.equals(
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
            if (password.length() < 12
                    || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
                throw new IllegalStateException(
                        "A senha inicial deve ter ao menos 12 caracteres e no máximo 72 bytes.");
            var owner =
                    roles.findById("ROLE_OWNER")
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "A migração não criou o papel ROLE_OWNER."));
            users.save(
                    new UserAccount(
                            username.trim(),
                            passwordEncoder.encode(password),
                            java.util.Set.of(owner)));
        };
    }
}
