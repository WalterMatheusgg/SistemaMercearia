package br.com.ferragens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@SpringBootApplication
@EnableMethodSecurity
public class FerragensApplication {

    public static void main(String[] args) {
        SpringApplication.run(FerragensApplication.class, args);
    }
}