package com.freezify;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;

/**
 * Runs the backend for local development without Docker or an installed PostgreSQL:
 * {@code ./mvnw spring-boot:test-run}. Data is kept in {@code .local/postgres} between runs.
 */
public class LocalFreezifyApplication {

    private static final int POSTGRES_PORT = 54329;

    public static void main(String[] args) throws IOException {
        // Stopped by the library's JVM shutdown hook.
        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setDataDirectory(Path.of(".local", "postgres").toAbsolutePath())
                .setCleanDataDirectory(false)
                .setPort(POSTGRES_PORT)
                .start();
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "");

        SpringApplication.from(FreezifyApplication::main)
                .withAdditionalProfiles("local")
                .run(args);
    }
}
