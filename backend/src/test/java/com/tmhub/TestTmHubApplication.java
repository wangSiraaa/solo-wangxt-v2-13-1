package com.tmhub;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;

/**
 * Dev runner: boots the real API on :8080 against an embedded PostgreSQL instance.
 * Usage: mvn spring-boot:test-run
 */
public class TestTmHubApplication {

    public static void main(String[] args) throws Exception {
        EmbeddedPostgres postgres = EmbeddedPostgres.start();
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "postgres");
        SpringApplication.run(TmHubApplication.class, args);
    }
}
