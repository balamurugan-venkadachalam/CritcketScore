package com.crickscore.producer.model;

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
 * Unit tests for {@link ScoreEvent} record.
 * Covers: construction, eventSequence() ordering key, withDefaults() factory, and JSON round-trip.
 */
class ScoreEventTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new ParameterNamesModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /** Sample event for reuse across tests. */
    private ScoreEvent sampleEvent() {
        return ScoreEvent.builder()
                .eventId("evt-001")
                .matchId("IPL-2025-MI-CSK-001")
                .inning(1)
                .over(3)
                .ball(4)
                .team("MI")
                .runs(4)
                .extras(0)
                .wicket(false)
                .totalRuns(54)
                .wickets(2)
                .timestamp(Instant.parse("2025-04-15T14:32:01Z"))
                .build();
    }

    // ─── eventSequence ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("eventSequence() – DynamoDB SK ordering key")
    class EventSequenceTests {

        @Test
        @DisplayName("formats as inning#over(2-padded)#ball")
        void formatsCorrectly() {
            ScoreEvent event = sampleEvent(); // inning=1, over=3, ball=4
            assertThat(event.eventSequence()).isEqualTo("1#03#4");
        }

        @Test
        @DisplayName("zero-pads over number for lexicographic sort")
        void zeroPadsOver() {
            ScoreEvent event = sampleEvent().toBuilder().over(0).ball(1).build();
            assertThat(event.eventSequence()).isEqualTo("1#00#1");
        }

        @Test
        @DisplayName("over=19 becomes 19 (no truncation)")
        void maxOver() {
            ScoreEvent event = sampleEvent().toBuilder().over(19).ball(6).build();
            assertThat(event.eventSequence()).isEqualTo("1#19#6");
        }

        @Test
        @DisplayName("inning=2 correctly prefixed")
        void secondInning() {
            ScoreEvent event = sampleEvent().toBuilder().inning(2).over(10).ball(3).build();
            assertThat(event.eventSequence()).isEqualTo("2#10#3");
        }

        @Test
        @DisplayName("second inning sorts after first inning lexicographically")
        void secondInningAfterFirst() {
            ScoreEvent first  = sampleEvent().toBuilder().inning(1).over(19).ball(6).build();
            ScoreEvent second = sampleEvent().toBuilder().inning(2).over(0).ball(1).build();
            assertThat(second.eventSequence()).isGreaterThan(first.eventSequence());
        }
    }

    // ─── withDefaults ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("withDefaults() – factory method")
    class WithDefaultsTests {

        @Test
        @DisplayName("preserves existing eventId if present")
        void preservesEventId() {
            ScoreEvent event = sampleEvent(); // has eventId="evt-001"
            ScoreEvent result = ScoreEvent.withDefaults(event);
            assertThat(result.eventId()).isEqualTo("evt-001");
        }

        @Test
        @DisplayName("generates UUID eventId when missing")
        void generatesEventId() {
            ScoreEvent event = sampleEvent().toBuilder().eventId(null).build();
            ScoreEvent result = ScoreEvent.withDefaults(event);
            assertThat(result.eventId()).isNotNull().hasSize(36); // UUID length
        }

        @Test
        @DisplayName("preserves existing timestamp if present")
        void preservesTimestamp() {
            ScoreEvent event = sampleEvent();
            ScoreEvent result = ScoreEvent.withDefaults(event);
            assertThat(result.timestamp()).isEqualTo(Instant.parse("2025-04-15T14:32:01Z"));
        }

        @Test
        @DisplayName("sets current timestamp when missing")
        void setsCurrentTimestamp() {
            ScoreEvent event = sampleEvent().toBuilder().timestamp(null).build();
            Instant before = Instant.now();
            ScoreEvent result = ScoreEvent.withDefaults(event);
            Instant after = Instant.now();
            assertThat(result.timestamp()).isBetween(before, after);
        }
    }

    // ─── Immutability ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Immutability – records are value objects")
    class ImmutabilityTests {

        @Test
        @DisplayName("two events with same fields are equal (record equality)")
        void recordEquality() {
            ScoreEvent a = sampleEvent();
            ScoreEvent b = sampleEvent();
            assertThat(a).isEqualTo(b);
        }

        @Test
        @DisplayName("toBuilder produces a distinct instance")
        void toBuilderDistinct() {
            ScoreEvent original = sampleEvent();
            ScoreEvent copy = original.toBuilder().runs(6).build();
            assertThat(copy).isNotEqualTo(original);
            assertThat(copy.runs()).isEqualTo(6);
            assertThat(original.runs()).isEqualTo(4); // unchanged
        }
    }

    // ─── JSON round-trip ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("JSON serialisation / deserialisation")
    class JsonTests {

        @Test
        @DisplayName("serialises then deserialises without data loss")
        void roundTrip() throws Exception {
            ScoreEvent original = sampleEvent();
            String json = mapper.writeValueAsString(original);
            ScoreEvent deserialized = mapper.readValue(json, ScoreEvent.class);
            assertThat(deserialized).isEqualTo(original);
        }

        @Test
        @DisplayName("timestamp serialised as ISO-8601 string (not epoch number)")
        void timestampIsIso8601() throws Exception {
            ScoreEvent event = sampleEvent();
            String json = mapper.writeValueAsString(event);
            assertThat(json).contains("2025-04-15T14:32:01Z");
            assertThat(json).doesNotContain("\"timestamp\":17"); // no epoch
        }

        @Test
        @DisplayName("null extras excluded from JSON (JsonInclude.NON_NULL)")
        void nullExtrasExcluded() throws Exception {
            ScoreEvent event = sampleEvent().toBuilder().extras(null).build();
            String json = mapper.writeValueAsString(event);
            assertThat(json).doesNotContain("extras");
        }

        @Test
        @DisplayName("matchId is present in JSON (Kafka partition key must be accessible)")
        void matchIdPresent() throws Exception {
            ScoreEvent event = sampleEvent();
            String json = mapper.writeValueAsString(event);
            assertThat(json).contains("\"matchId\":\"IPL-2025-MI-CSK-001\"");
        }
    }
}
