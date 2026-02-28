package com.crickscore.consumer.repository;

import com.crickscore.consumer.model.ScoreEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ScoreEventMapper}.
 * Verifies attribute mapping logic — no DynamoDB connection required.
 */
class ScoreEventMapperTest {

    private ScoreEventMapper mapper;

    private static final ScoreEvent BALL_EVENT = new ScoreEvent(
            UUID.randomUUID().toString(), // eventId
            "IPL-2025-MI-CSK-001", // matchId
            1, // inning
            4, // over (0-indexed, so over 5)
            3, // ball
            "MI", // team
            4, // runs
            null, // extras
            false, // wicket
            87, // totalRuns
            2, // wickets
            Instant.parse("2025-04-15T14:32:01Z") // timestamp
    );

    @BeforeEach
    void setUp() {
        mapper = new ScoreEventMapper();
    }

    @Nested
    @DisplayName("toEventStoreItem()")
    class EventStoreItem {

        @Test
        @DisplayName("matchId is the partition key")
        void matchId_isPk() {
            Map<String, AttributeValue> item = mapper.toEventStoreItem(BALL_EVENT);
            assertThat(item.get("matchId").s()).isEqualTo("IPL-2025-MI-CSK-001");
        }

        @Test
        @DisplayName("eventSequence sort key is formatted as inning#over#ball (zero-padded over)")
        void eventSequence_isSortKey_withZeroPaddedOver() {
            Map<String, AttributeValue> item = mapper.toEventStoreItem(BALL_EVENT);
            // inning=1, over=4 → "04", ball=3
            assertThat(item.get("eventSequence").s()).isEqualTo("1#04#3");
        }

        @Test
        @DisplayName("eventId is stored")
        void eventId_isStored() {
            Map<String, AttributeValue> item = mapper.toEventStoreItem(BALL_EVENT);
            assertThat(item.get("eventId").s()).isEqualTo(BALL_EVENT.eventId());
        }

        @Test
        @DisplayName("runs stored as DynamoDB number type")
        void runs_storedAsNumber() {
            Map<String, AttributeValue> item = mapper.toEventStoreItem(BALL_EVENT);
            assertThat(item.get("runs").n()).isEqualTo("4");
        }

        @Test
        @DisplayName("wicket stored as DynamoDB bool")
        void wicket_storedAsBool() {
            Map<String, AttributeValue> item = mapper.toEventStoreItem(BALL_EVENT);
            assertThat(item.get("wicket").bool()).isFalse();
        }

        @Test
        @DisplayName("null extras are omitted from the item")
        void nullExtras_omitted() {
            Map<String, AttributeValue> item = mapper.toEventStoreItem(BALL_EVENT);
            assertThat(item).doesNotContainKey("extras");
        }

        @Test
        @DisplayName("non-null extras are included as a number")
        void nonNullExtras_included() {
            ScoreEvent withExtras = BALL_EVENT.toBuilder().extras(2).build();
            Map<String, AttributeValue> item = mapper.toEventStoreItem(withExtras);
            assertThat(item.get("extras").n()).isEqualTo("2");
        }

        @Test
        @DisplayName("timestamp stored as ISO-8601 string")
        void timestamp_storedAsIso8601() {
            Map<String, AttributeValue> item = mapper.toEventStoreItem(BALL_EVENT);
            assertThat(item.get("timestamp").s()).isEqualTo("2025-04-15T14:32:01Z");
        }

        @Test
        @DisplayName("TTL attribute is set to ~90 days after timestamp")
        void ttl_setTo90DaysAfterTimestamp() {
            Map<String, AttributeValue> item = mapper.toEventStoreItem(BALL_EVENT);
            long ttl = Long.parseLong(item.get("ttl").n());
            long expected = Instant.parse("2025-04-15T14:32:01Z").plusSeconds(90L * 24 * 3600).getEpochSecond();
            assertThat(ttl).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("toLiveScoreExpressionValues()")
    class LiveScoreExpressionValues {

        @Test
        @DisplayName("contains all required expression attribute keys")
        void containsAllRequiredKeys() {
            Map<String, AttributeValue> values = mapper.toLiveScoreExpressionValues(BALL_EVENT);
            assertThat(values).containsKeys(
                    ":matchId", ":totalRuns", ":wickets", ":inning",
                    ":over", ":ball", ":runs", ":wicket", ":lastUpdated");
        }

        @Test
        @DisplayName(":totalRuns reflects event.totalRuns()")
        void totalRuns_fromEvent() {
            Map<String, AttributeValue> values = mapper.toLiveScoreExpressionValues(BALL_EVENT);
            assertThat(values.get(":totalRuns").n()).isEqualTo("87");
        }

        @Test
        @DisplayName(":wickets reflects event.wickets()")
        void wickets_fromEvent() {
            Map<String, AttributeValue> values = mapper.toLiveScoreExpressionValues(BALL_EVENT);
            assertThat(values.get(":wickets").n()).isEqualTo("2");
        }

        @Test
        @DisplayName(":wicket is false when no wicket fell")
        void isWicket_false() {
            Map<String, AttributeValue> values = mapper.toLiveScoreExpressionValues(BALL_EVENT);
            assertThat(values.get(":wicket").bool()).isFalse();
        }

        @Test
        @DisplayName(":matchId matches event.matchId()")
        void matchId_fromEvent() {
            Map<String, AttributeValue> values = mapper.toLiveScoreExpressionValues(BALL_EVENT);
            assertThat(values.get(":matchId").s()).isEqualTo("IPL-2025-MI-CSK-001");
        }
    }
}
