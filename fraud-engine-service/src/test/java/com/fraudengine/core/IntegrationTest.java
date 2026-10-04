package com.fraudengine.core;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "environment=it",
        "app.kafka-settings.consumer-concurrency=1",
        "app.kafka-settings.retry-attempts=1",
        "app.kafka-settings.retry-initial-interval-ms=200",
        "otel.sdk.disabled=true"
})
public abstract class IntegrationTest {

    // same images as docker-compose, started once for every integration test in the run
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.7.0");

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void containerProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeEach
    void clearTables() {
        jdbcTemplate.execute("""
                TRUNCATE evaluated_transactions, rule_hits, outbox_events, dead_letter, dlt_audit_log, bad_locations
                """);
    }
}
