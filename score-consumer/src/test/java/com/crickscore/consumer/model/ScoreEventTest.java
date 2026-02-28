package com.crickscore.consumer.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScoreEvent}.
 *
 * <p>
 * Validates:
 * <ul>
 * <li>DynamoDB sort-key format from {@link ScoreEvent#eventSequence()}</li>
 * <li>{@link ScoreEvent#withDefaults(ScoreEvent)} factory behaviour</li>
 * <li>Record equality and immutability invariants</li>
 * <li>JSON round-trip via Jackson (the same ObjectMapper used in
 * production)</li>
 * </ul>
 */
class ScoreEventTest {

    private static final String MATCH_ID = "IPL-2025-MI-CSK-001";
    private static final String EVENT_ID = "test-event-id-001";
    private static final Instant NOW = Instant.parse("2025-04-15T14:32:01Z");

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new ParameterNamesModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ─── eventSequence() ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("eventSequence()")
    class EventSequenceTests {

        @Test
        @DisplayName("formats inning=1, over=0, ball=1 as '1#00#1'")
        void sequence_firstBallOfFirstInning() {
            ScoreEvent event = buildEvent(1, 0, 1);
            assertThat(event.eventSequence()).isEqualTo("1#00#1");
        }

        @Test
        @DisplayName("formats inning=1, over=19, ball=6 as '1#19#6'")
        void sequence_lastBallOfFirstInning() {
            ScoreEvent event = buildEvent(1, 19, 6);
            assertThat(event.eventSequence()).isEqualTo("1#19#6");
        }

        @Test
        @DisplayName("formats inning=2, over=0, ball=1 as '2#00#1'")
        void sequence_firstBallOfSecondInning() {
            ScoreEvent event = buildEvent(2, 0, 1);
            assertThat(event.eventSequence()).isEqualTo("2#00#1");
        }

        @Test
        @DisplayName("over is zero-padded to 2 digits for lexicographic sort (over 3 → '03')")
        void sequence_overIsZeroPadded() {
            ScoreEvent event = buildEvent(1, 3, 2);
            assertThat(event.eventSequence()).isEqualTo("1#03#2");
        }

        @Test
        @DisplayName("'1#03#2' sorts before '1#10#1' lexicographically")
        void sequence_lexicographicOrdering() {
            String earlier = buildEvent(1, 3, 2).eventSequence();
            String later = buildEvent(1, 10, 1).eventSequence();
            assertThat(earlier).isLessThan(later);
        }

        @Test
        @DisplayName("second inning sequence sorts after all first-inning sequences")
        void sequence_secondInningAfterFirst() {
            String lastFirstInning = buildEvent(1, 19, 6).eventSequence();
            String firstSecondInning = buildEvent(2, 0, 1).eventSequence();
            assertThat(firstSecondInning).isGreaterThan(lastFirstInning);
        }
    }

    // ─── withDefaults() ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("withDefaults()")
    class WithDefaultsTests {

        @Test
        @DisplayName("auto-generates eventId when null")
        void withDefaults_generatesEventId() {
            ScoreEvent prototype = buildEvent(1, 0, 1).toBuilder().eventId(null).build();
            ScoreEvent result = ScoreEvent.withDefaults(prototype);
            assertThat(result.eventId()).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("preserves explicit eventId when provided")
        void withDefaults_preservesExistingEventId() {
            ScoreEvent prototype = buildEvent(1, 0, 1);
            ScoreEvent result = ScoreEvent.withDefaults(prototype);
            assertThat(result.eventId()).isEqualTo(EVENT_ID);
        }

        @Test
        @DisplayName("auto-generates timestamp when null")
        void withDefaults_generatesTimestamp() {
            ScoreEvent prototype = buildEvent(1, 0, 1).toBuilder().timestamp(null).build();
            Instant before = Instant.now();
            ScoreEvent result = ScoreEvent.withDefaults(prototype);
            assertThat(result.timestamp()).isNotNull().isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("preserves explicit timestamp when provided")
        void withDefaults_preservesExistingTimestamp() {
            ScoreEvent prototype = buildEvent(1, 0, 1);
            ScoreEvent result = ScoreEvent.withDefaults(prototype);
            assertThat(result.timestamp()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("two calls to withDefaults with null eventId generate different UUIDs")
        void withDefaults_generatesUniqueIds() {
            ScoreEvent p1 = buildEvent(1, 0, 1).toBuilder().eventId(null).build();
            ScoreEvent p2 = buildEvent(1, 0, 2).toBuilder().eventId(null).build();
            assertThat(ScoreEvent.withDefaults(p1).eventId())
                    .isNotEqualTo(ScoreEvent.withDefaults(p2).eventId());
        }
    }

    // ─── Record equality ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Record equality")
    class EqualityTests {

        @Test
        @DisplayName("two records with the same fields are equal")
        void equality_sameFieldsAreEqual() {
            ScoreEvent a = buildEvent(1, 3, 2);
            ScoreEvent b = buildEvent(1, 3, 2);
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("records with different over are not equal")
        void equality_differentOverNotEqual() {
            assertThat(buildEvent(1, 3, 2)).isNotEqualTo(buildEvent(1, 4, 2));
        }

        @Test
        @DisplayName("toBuilder() creates an equivalent copy")
        void equality_toBuilderCopy() {
            ScoreEvent original = buildEvent(1, 3, 2);
            ScoreEvent copy = original.toBuilder().build();
            assertThat(copy).isEqualTo(original);
        }
    }

    // ─── JSON round-trip ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("JSON round-trip")
    class JsonTests {

        @Test
        @DisplayName("serialises and deserialises all fields correctly")
        void json_roundTrip() throws Exception {
            ScoreEvent original = buildEvent(1, 3, 2);
            String json = objectMapper.writeValueAsString(original);
            ScoreEvent restored = objectMapper.readValue(json, ScoreEvent.class);
            assertThat(restored).isEqualTo(original);
        }

        @Test
        @DisplayName("timestamp serialises as ISO-8601 string, not epoch number")
        void json_timestampAsIso8601() throws Exception {
            ScoreEvent event = buildEvent(1, 0, 1);
            String json = objectMapper.writeValueAsString(event);
            assertThat(json).contains("2025-04-15T14:32:01Z");
            assertThat(json).doesNotContainPattern("\"timestamp\":\\s*\\d{10}");
        }

        @Test
        @DisplayName("null extras field is omitted from JSON (@JsonInclude NON_NULL)")
        void json_nullExtrasOmitted() throws Exception {
            ScoreEvent event = buildEvent(1, 0, 1).toBuilder().extras(null).build();
            String json = objectMapper.writeValueAsString(event);
            assertThat(json).doesNotContain("extras");
        }

        @Test
        @DisplayName("JSON contains matchId field")
        void json_containsMatchId() throws Exception {
            ScoreEvent event = buildEvent(1, 0, 1);
            String json = objectMapper.writeValueAsString(event);
            assertThat(json).contains(MATCH_ID);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ScoreEvent buildEvent(int inning, int over, int ball) {
        return ScoreEvent.builder()
                .eventId(EVENT_ID)
                .matchId(MATCH_ID)
                .inning(inning)
                .over(over)
                .ball(ball)
                .team("MI")
                .runs(4)
                .extras(0)
                .wicket(false)
                .totalRuns(67)
                .wickets(2)
                .timestamp(NOW)
                .build();
    }
}
