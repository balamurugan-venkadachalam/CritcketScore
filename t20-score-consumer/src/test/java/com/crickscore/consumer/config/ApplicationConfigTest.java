package com.crickscore.consumer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.crickscore.consumer.model.ScoreEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@link ApplicationConfig} beans are correctly configured.
 *
 * <p>
 * Specifically validates:
 * <ul>
 * <li>The primary {@link ObjectMapper} is present and correctly
 * configured.</li>
 * <li>Timestamps are serialised as ISO-8601 strings, not epoch longs.</li>
 * <li>{@link ScoreEvent} records can round-trip through Jackson without
 * {@code @JsonProperty}.</li>
 * </ul>
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
class ApplicationConfigTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("primary ObjectMapper bean is present")
    void objectMapper_isPresent() {
        assertThat(objectMapper).isNotNull();
    }

    @Test
    @DisplayName("ObjectMapper does NOT write dates as timestamps (ISO-8601 mode)")
    void objectMapper_disablesWriteDatesAsTimestamps() {
        assertThat(objectMapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                .isFalse();
    }

    @Test
    @DisplayName("ObjectMapper serialises Instant as ISO-8601 string")
    void objectMapper_instantAsIso8601() throws Exception {
        Instant instant = Instant.parse("2025-04-15T14:32:01Z");
        String json = objectMapper.writeValueAsString(instant);
        assertThat(json).isEqualTo("\"2025-04-15T14:32:01Z\"");
    }

    @Test
    @DisplayName("ObjectMapper can round-trip ScoreEvent record without @JsonProperty")
    void objectMapper_scoreEventRoundTrip() throws Exception {
        ScoreEvent original = ScoreEvent.builder()
                .eventId("test-evt-001")
                .matchId("IPL-2025-MI-CSK-001")
                .inning(1).over(3).ball(2)
                .team("MI").runs(4).extras(0)
                .wicket(false).totalRuns(67).wickets(2)
                .timestamp(Instant.parse("2025-04-15T14:32:01Z"))
                .build();

        String json = objectMapper.writeValueAsString(original);
        ScoreEvent restored = objectMapper.readValue(json, ScoreEvent.class);

        assertThat(restored).isEqualTo(original);
    }
}
