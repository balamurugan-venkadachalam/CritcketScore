package com.crickscore.consumer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link AppProperties} binds correctly from
 * {@code application.yml}
 * when the {@code local} profile is active.
 *
 * <p>
 * Uses a full Spring context ({@code @SpringBootTest}) to test the actual
 * YAML binding, not just default field values.
 */
@SpringBootTest
@ActiveProfiles("local")
@EmbeddedKafka(partitions = 1, topics = { "t20-match-scores" })
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "aws.dynamodb.endpoint-override=",
        "aws.secretsmanager.endpoint-override=",
        "spring.kafka.listener.auto-startup=false"
})
class AppPropertiesTest {

    @Autowired
    private AppProperties appProperties;

    // ─── Kafka Consumer ───────────────────────────────────────────────────────

    @Test
    @DisplayName("concurrency binds to 8 (from application.yml)")
    void kafka_concurrencyIsBound() {
        assertThat(appProperties.getKafka().getConsumer().getConcurrency()).isEqualTo(8);
    }

    @Test
    @DisplayName("consumer clientId default is set")
    void kafka_clientIdHasValue() {
        assertThat(appProperties.getKafka().getConsumer().getClientId())
                .isNotBlank()
                .startsWith("t20-score-consumer");
    }

    // ─── Kafka Topics ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("score-events topic name is 't20-match-scores'")
    void kafka_scoreEventsTopicBound() {
        assertThat(appProperties.getKafka().getTopic().getScoreEvents())
                .isEqualTo("t20-match-scores");
    }

    @Test
    @DisplayName("retry-1 topic name is 't20-match-scores-retry-1'")
    void kafka_retry1TopicBound() {
        assertThat(appProperties.getKafka().getTopic().getRetry1())
                .isEqualTo("t20-match-scores-retry-1");
    }

    @Test
    @DisplayName("DLT topic name is 't20-match-scores-dlt'")
    void kafka_dltTopicBound() {
        assertThat(appProperties.getKafka().getTopic().getDlt())
                .isEqualTo("t20-match-scores-dlt");
    }

    // ─── DynamoDB Tables ──────────────────────────────────────────────────────

    @Test
    @DisplayName("score-events table name is 't20-score-events'")
    void dynamodb_scoreEventsTableBound() {
        assertThat(appProperties.getDynamodb().getTable().getScoreEvents())
                .isEqualTo("t20-score-events");
    }

    @Test
    @DisplayName("live-scores table name is 't20-live-scores'")
    void dynamodb_liveScoresTableBound() {
        assertThat(appProperties.getDynamodb().getTable().getLiveScores())
                .isEqualTo("t20-live-scores");
    }

    @Test
    @DisplayName("replay-state table name is 't20-replay-state'")
    void dynamodb_replayStateTableBound() {
        assertThat(appProperties.getDynamodb().getTable().getReplayState())
                .isEqualTo("t20-replay-state");
    }
}
