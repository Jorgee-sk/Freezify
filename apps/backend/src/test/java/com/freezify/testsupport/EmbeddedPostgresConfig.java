package com.freezify.testsupport;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Points the application at a real PostgreSQL started from embedded binaries, so Flyway migrations and
 * queries are exercised against the production engine without needing Docker.
 */
@TestConfiguration(proxyBeanMethods = false)
public class EmbeddedPostgresConfig {

    /** One server for the whole test JVM; it is stopped by the library's shutdown hook. */
    private static final class Holder {
        private static final EmbeddedPostgres INSTANCE = start();

        private static EmbeddedPostgres start() {
            try {
                return EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Bean
    DynamicPropertyRegistrar embeddedPostgresProperties() {
        EmbeddedPostgres postgres = Holder.INSTANCE;
        return registry -> {
            registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
            registry.add("spring.datasource.username", () -> "postgres");
            registry.add("spring.datasource.password", () -> "");
        };
    }
}
