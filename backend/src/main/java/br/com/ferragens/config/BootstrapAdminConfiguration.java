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
@Profile("local")
public class BootstrapAdminConfiguration {

    @Bean
    CommandLineRunner bootstrapAdmin(
            UserAccountRepository users,
            RoleRepository roles,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.enabled:true}") boolean enabled,
            @Value("${app.bootstrap.admin.username:}") String username,
            @Value("${app.bootstrap.admin.password:}") String password) {
        return args -> {
            if (!enabled || users.count() > 0) {
                return;
            }
            if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
                throw new IllegalStateException("Defina BOOTSTRAP_ADMIN_USERNAME e BOOTSTRAP_ADMIN_PASSWORD no .env local.");
            }
            var owner = roles.findById("ROLE_OWNER")
                    .orElseThrow(() -> new IllegalStateException("A migração não criou o papel ROLE_OWNER."));
            users.save(new UserAccount(username.trim(), passwordEncoder.encode(password), java.util.Set.of(owner)));
        };
    }
}