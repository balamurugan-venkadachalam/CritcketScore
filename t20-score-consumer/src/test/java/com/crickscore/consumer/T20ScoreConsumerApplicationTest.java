package com.crickscore.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test — verifies the Spring application context starts successfully.
 *
 * <p>
 * Uses an embedded Kafka broker to satisfy the Kafka auto-configuration,
 * and overrides the bootstrap-servers property so the app doesn't attempt
 * to connect to a real broker at localhost:9092 during tests.
 */
@SpringBootTest
@ActiveProfiles("local")
@EmbeddedKafka(partitions = 1, topics = {
        "t20-match-scores",
        "t20-match-scores-retry-1",
        "t20-match-scores-retry-2",
        "t20-match-scores-retry-3",
        "t20-match-scores-dlt"
})
@TestPropertySource(properties = {
        // Override bootstrap-servers to point at the embedded broker
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        // Disable LocalStack / AWS SDK connectivity during context load test
        "aws.dynamodb.endpoint-override=",
        "aws.secretsmanager.endpoint-override="
})
class T20ScoreConsumerApplicationTest {

    @Test
    @DisplayName("Spring application context loads without errors")
    void contextLoads() {
        // If the context fails to start, this test fails with a descriptive error.
        // No assertions needed — the @SpringBootTest startup IS the assertion.
    }
}
