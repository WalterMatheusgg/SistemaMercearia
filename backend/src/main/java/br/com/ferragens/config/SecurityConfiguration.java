package br.com.ferragens.config;

import br.com.ferragens.auth.UserAccountRepository;
import br.com.ferragens.commerce.Db;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Configuration
public class SecurityConfiguration {

    /**
     * Arquivos públicos imutáveis não precisam abrir sessão nem gerar token CSRF.
     * Na Vercel, isso permite que a CDN sirva JS/CSS sem inicializar o banco.
     * O matcher é propositalmente restrito a GET/HEAD e arquivos estáticos.
     */
    @Bean
    @Order(0)
    SecurityFilterChain publicAssets(HttpSecurity http) throws Exception {
        RequestMatcher staticAsset = request -> {
            if (!("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
                return false;
            }
            String path = request.getServletPath();
            return path.matches("^/[A-Za-z0-9_.-]+-[A-Za-z0-9_-]{7,}\\.(js|css)$")
                    || "/loja.svg".equals(path)
                    || "/favicon.ico".equals(path)
                    || path.startsWith("/assets/");
        };
        return http.securityMatcher(staticAsset)
                .csrf(csrf -> csrf.disable())
                .securityContext(context -> context.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService userDetailsService(UserAccountRepository users) {
        return username ->
                users.findByUsername(username)
                        .map(
                                account ->
                                        User.withUsername(account.getUsername())
                                                .password(account.getPasswordHash())
                                                .authorities(
                                                        account.getRoles().stream()
                                                                .map(role -> role.getCode())
                                                                .toArray(String[]::new))
                                                .disabled(!account.isEnabled())
                                                .build())
                        .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider::authenticate;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(
            CookieCsrfTokenRepository csrfTokens) {
        var csrf = new CsrfAuthenticationStrategy(csrfTokens);
        csrf.setRequestHandler(new SpaCsrfTokenRequestHandler());
        return new CompositeSessionAuthenticationStrategy(
                List.of(new ChangeSessionIdAuthenticationStrategy(), csrf));
    }

    @Bean
    LogoutHandler applicationLogoutHandler(CookieCsrfTokenRepository csrfTokens) {
        return new CompositeLogoutHandler(
                new SecurityContextLogoutHandler(), new CsrfLogoutHandler(csrfTokens));
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokens,
            ObjectMapper objectMapper,
            Db db)
            throws Exception {
        AccessDeniedHandler accessDeniedHandler =
                (request, response, exception) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    objectMapper.writeValue(
                            response.getOutputStream(),
                            ProblemDetail.forStatusAndDetail(
                                    HttpStatus.FORBIDDEN,
                                    "Você não tem permissão para esta operação."));
                };
        AuthenticationEntryPoint authenticationEntryPoint =
                (request, response, exception) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    objectMapper.writeValue(
                            response.getOutputStream(),
                            ProblemDetail.forStatusAndDetail(
                                    HttpStatus.UNAUTHORIZED, "Autenticação necessária."));
                };

        http.addFilterBefore(new AuthRateLimitFilter(), CsrfFilter.class)
                .addFilterBefore(new SessionValidationFilter(db), AuthorizationFilter.class)
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(csrfTokens)
                                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .securityContext(
                        context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(authenticationEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers(
                                                "/api/auth/csrf",
                                                "/api/auth/login",
                                                "/api/auth/logout",
                                                "/api/auth/register",
                                                "/api/auth/password/**",
                                                "/api/auth/email/verify",
                                                "/api/catalog/preview",
                                                "/actuator/health")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/catalog",
                                                "/api/catalog/**",
                                                "/",
                                                "/index.html",
                                                "/*.js",
                                                "/*.css",
                                                "/*.ico",
                                                "/*.svg",
                                                "/assets/**",
                                                "/media/**",
                                                "/store",
                                                "/cart",
                                                "/login",
                                                "/register",
                                                "/forgot-password",
                                                "/reset-password",
                                                "/verify-email",
                                                "/account",
                                                "/admin",
                                                "/admin/**")
                                        .permitAll()
                                        .requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .authenticated()
                                        .requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .denyAll()
                                        .anyRequest()
                                        .authenticated())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());
        return http.build();
    }
}
