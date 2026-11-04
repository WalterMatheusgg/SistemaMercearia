package br.com.ferragens;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Usa containers por padrão; permite serviços locais com schema isolado em ambientes sem Docker. */
final class IntegrationServices {
    private static final String EXTERNAL_JDBC = System.getenv("FERRAGENS_TEST_JDBC_URL");
    private static final String EXTERNAL_MAILPIT = System.getenv("FERRAGENS_TEST_MAILPIT_HTTP_URL");
    private static final PostgreSQLContainer<?> POSTGRES = startPostgres();
    private static final GenericContainer<?> MAILPIT = startMailpit();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (MAILPIT != null) MAILPIT.stop();
            if (POSTGRES != null) POSTGRES.stop();
        }));
    }

    private IntegrationServices() { }

    private static PostgreSQLContainer<?> startPostgres() {
        if (EXTERNAL_JDBC != null && !EXTERNAL_JDBC.isBlank()) return null;
        var container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.6-alpine"));
        container.start(); return container;
    }

    private static GenericContainer<?> startMailpit() {
        if (EXTERNAL_MAILPIT != null && !EXTERNAL_MAILPIT.isBlank()) return null;
        var container = new GenericContainer<>(DockerImageName.parse("axllent/mailpit:v1.27.11"))
                .withExposedPorts(1025, 8025);
        container.start(); return container;
    }

    static void configure(DynamicPropertyRegistry properties, String schema) {
        String jdbc = POSTGRES == null ? EXTERNAL_JDBC : POSTGRES.getJdbcUrl();
        properties.add("spring.datasource.url", () -> jdbc + (jdbc.contains("?") ? "&" : "?") + "currentSchema=" + schema);
        properties.add("spring.datasource.username", () -> POSTGRES == null ? required("FERRAGENS_TEST_DB_USERNAME") : POSTGRES.getUsername());
        properties.add("spring.datasource.password", () -> POSTGRES == null ? System.getenv().getOrDefault("FERRAGENS_TEST_DB_PASSWORD", "") : POSTGRES.getPassword());
        properties.add("spring.flyway.schemas", () -> schema);
        properties.add("spring.flyway.default-schema", () -> schema);
        properties.add("spring.jpa.properties.hibernate.default_schema", () -> schema);
        properties.add("spring.mail.host", () -> MAILPIT == null ? System.getenv().getOrDefault("FERRAGENS_TEST_SMTP_HOST", "127.0.0.1") : MAILPIT.getHost());
        properties.add("spring.mail.port", () -> MAILPIT == null ? Integer.parseInt(required("FERRAGENS_TEST_SMTP_PORT")) : MAILPIT.getMappedPort(1025));
    }

    static String mailpitUrl() {
        return MAILPIT == null ? EXTERNAL_MAILPIT : "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Configure " + name + " para os serviços de teste externos.");
        return value;
    }
}
