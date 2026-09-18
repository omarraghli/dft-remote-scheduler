package cires.dft.remotescheduler;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for tests that need the database.
 *
 * <p>Runs against a real PostgreSQL rather than an in-memory stand-in, with Liquibase enabled
 * and {@code ddl-auto=validate}, so every test run checks that the changelog and the entities
 * still agree. A column renamed in one but not the other fails the build here instead of at
 * deploy time.
 *
 * <p>The container is {@code static} and deliberately never stopped: one Postgres is started for
 * the whole test JVM and Ryuk removes it afterwards, rather than paying the startup cost per
 * test class.
 */
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("dft_remote")
                    .withUsername("dft_remote")
                    .withPassword("dft_remote");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
