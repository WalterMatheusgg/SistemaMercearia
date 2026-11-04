package br.com.ferragens.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

@Configuration
@Profile("local")
public class LocalMailSmokeConfiguration {

    @Bean
    CommandLineRunner sendLocalMailSmokeTest(
            JavaMailSender mailSender,
            @Value("${app.mail.smoke-test:false}") boolean enabled,
            @Value("${app.mail.smoke-test-recipient:}") String recipient) {
        return args -> {
            if (!enabled) {
                return;
            }
            if (!StringUtils.hasText(recipient)) {
                throw new IllegalStateException("Defina app.mail.smoke-test-recipient para o teste local de e-mail.");
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("sistema@ferragens.local");
            message.setTo(recipient.trim());
            message.setSubject("ferragens-local-smoke-test");
            message.setText("Mensagem local de verificação do Spring Mail.");
            mailSender.send(message);
        };
    }
}