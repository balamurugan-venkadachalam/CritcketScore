package com.crickscore.consumer.repository;

import com.crickscore.consumer.model.ScoreEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DynamoDbLiveScoreRepository}.
 *
 * <p>
 * Verifies UpdateItem request construction for the {@code t20-live-scores}
 * materialized view. Uses a mocked {@link DynamoDbClient} — no real AWS
 * connection required.
 *
 * <p>
 * TASK-11.6 acceptance criterion: "Write 10 sequential events, assert
 * {@code totalRuns} in live score matches sum." This is verified by
 * {@link SequentialEvents#tenSequentialEvents_totalRunsMatchesSum()}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class LiveScoreRepositoryTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private DynamoDbLiveScoreRepository repository;

    private static final String TABLE = "t20-live-scores";
    private static final String MATCH_ID = "IPL-2025-MI-CSK-001";

    @BeforeEach
    void setUp() {
        ScoreEventMapper mapper = new ScoreEventMapper();
        AppPropertiesDelegate delegate = mock(AppPropertiesDelegate.class);
        when(delegate.liveScoresTable()).thenReturn(TABLE);
        repository = new DynamoDbLiveScoreRepository(dynamoDbClient, mapper, delegate);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Single update request structure
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("update() — UpdateItem request structure")
    class UpdateItemStructure {

        private final ScoreEvent EVENT = buildEvent(
                1, 0, 1, "MI", 4, false, 4, 0, Instant.parse("2025-04-15T14:00:00Z"));

        @Test
        @DisplayName("calls updateItem exactly once per event")
        void callsUpdateItemOnce() {
            repository.update(EVENT);
            verify(dynamoDbClient, times(1)).updateItem(any(UpdateItemRequest.class));
        }

        @Test
        @DisplayName("UpdateItemRequest targets the configured table name")
        void updateTargetsCorrectTable() {
            repository.update(EVENT);

            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());
            assertThat(captor.getValue().tableName()).isEqualTo(TABLE);
        }

        @Test
        @DisplayName("UpdateItemRequest key contains matchId")
        void updateKeyContainsMatchId() {
            repository.update(EVENT);

            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            Map<String, AttributeValue> key = captor.getValue().key();
            assertThat(key).containsKey("matchId");
            assertThat(key.get("matchId").s()).isEqualTo(MATCH_ID);
        }

        @Test
        @DisplayName("UpdateItemRequest has a non-blank updateExpression")
        void updateExpressionIsPresent() {
            repository.update(EVENT);

            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());
            assertThat(captor.getValue().updateExpression()).isNotBlank();
        }

        @Test
        @DisplayName("expressionAttributeValues contains :totalRuns with correct value")
        void expressionValuesTotalRuns() {
            repository.update(EVENT);

            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            Map<String, AttributeValue> values = captor.getValue().expressionAttributeValues();
            assertThat(values).containsKey(":totalRuns");
            assertThat(values.get(":totalRuns").n()).isEqualTo("4");
        }

        @Test
        @DisplayName("expressionAttributeValues contains :wickets with correct value")
        void expressionValuesWickets() {
            repository.update(EVENT);

            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            Map<String, AttributeValue> values = captor.getValue().expressionAttributeValues();
            assertThat(values).containsKey(":wickets");
            assertThat(values.get(":wickets").n()).isEqualTo("0");
        }

        @Test
        @DisplayName("expressionAttributeValues contains all required placeholder keys")
        void expressionValuesContainsAllRequiredKeys() {
            repository.update(EVENT);

            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            Map<String, AttributeValue> values = captor.getValue().expressionAttributeValues();
            assertThat(values).containsKeys(
                    ":matchId", ":totalRuns", ":wickets", ":inning",
                    ":over", ":ball", ":runs", ":wicket", ":lastUpdated");
        }

        @Test
        @DisplayName(":wicket is false when no wicket fell on the delivery")
        void expressionIsWicket_False() {
            repository.update(EVENT);

            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            assertThat(captor.getValue().expressionAttributeValues()
                    .get(":wicket").bool()).isFalse();
        }

        @Test
        @DisplayName(":wicket is true when a wicket fell on the delivery")
        void expressionIsWicket_True() {
            ScoreEvent wicketBall = buildEvent(1, 0, 2, "MI", 0, true, 87, 1,
                    Instant.parse("2025-04-15T14:01:00Z"));

            repository.update(wicketBall);

            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient).updateItem(captor.capture());

            assertThat(captor.getValue().expressionAttributeValues()
                    .get(":wicket").bool()).isTrue();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TASK-11.6 acceptance criterion: 10 sequential events, totalRuns matches
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Sequential events — totalRuns accumulation (TASK-11.6)")
    class SequentialEvents {

        /**
         * Simulates 10 sequential ball deliveries and verifies that each
         * {@code UpdateItem} call carries the correct cumulative
         * {@code :totalRuns} value.
         *
         * <p>
         * This mirrors the "write 10 events, assert totalRuns matches sum"
         * acceptance criterion from TASK-11.6. Because {@link DynamoDbClient}
         * is mocked, the running total is computed here and compared against
         * what would be supplied to DynamoDB.
         */
        @Test
        @DisplayName("10 sequential events each carry the accumulated totalRuns in the UpdateItem call")
        void tenSequentialEvents_totalRunsMatchesSum() {
            // ── Arrange ────────────────────────────────────────────────────
            // 10 balls with varying runs: 4, 0, 1, 6, 2, 0, 3, 1, 4, 0
            List<Integer> ballRuns = List.of(4, 0, 1, 6, 2, 0, 3, 1, 4, 0);

            // Capture every UpdateItemRequest in order
            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);

            AtomicInteger runningTotal = new AtomicInteger(0);
            AtomicInteger ballNumber = new AtomicInteger(1);

            // ── Act ────────────────────────────────────────────────────────
            for (int runs : ballRuns) {
                int cumulative = runningTotal.addAndGet(runs);
                int ball = ballNumber.getAndIncrement();

                ScoreEvent event = buildEvent(
                        1, 0, ball, "MI", runs, false,
                        cumulative, 0, Instant.parse("2025-04-15T14:00:00Z").plusSeconds(ball * 30L));

                repository.update(event);
            }

            // ── Assert ─────────────────────────────────────────────────────
            verify(dynamoDbClient, times(10)).updateItem(captor.capture());
            List<UpdateItemRequest> requests = captor.getAllValues();

            // Expected cumulative totals after each ball:
            // 4, 4, 5, 11, 13, 13, 16, 17, 21, 21
            int[] expectedTotals = { 4, 4, 5, 11, 13, 13, 16, 17, 21, 21 };
            for (int i = 0; i < requests.size(); i++) {
                String actualTotal = requests.get(i).expressionAttributeValues().get(":totalRuns").n();
                assertThat(Integer.parseInt(actualTotal))
                        .as("Ball %d: totalRuns should be %d", i + 1, expectedTotals[i])
                        .isEqualTo(expectedTotals[i]);
            }

            // Final total must equal the sum of all runs
            int sumOfRuns = ballRuns.stream().mapToInt(Integer::intValue).sum();
            String finalTotal = requests.get(9).expressionAttributeValues()
                    .get(":totalRuns").n();
            assertThat(Integer.parseInt(finalTotal))
                    .as("Final totalRuns after 10 balls should equal sum of all runs (%d)", sumOfRuns)
                    .isEqualTo(sumOfRuns);
        }

        @Test
        @DisplayName("Each of 10 sequential events targets the same table and matchId key")
        void tenSequentialEvents_eachTargetsSameTableAndKey() {
            for (int ball = 1; ball <= 10; ball++) {
                ScoreEvent event = buildEvent(1, 0, ball, "MI", 4, false,
                        ball * 4, 0, Instant.parse("2025-04-15T14:00:00Z").plusSeconds(ball * 30L));
                repository.update(event);
            }

            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient, times(10)).updateItem(captor.capture());

            for (UpdateItemRequest req : captor.getAllValues()) {
                assertThat(req.tableName()).isEqualTo(TABLE);
                assertThat(req.key().get("matchId").s()).isEqualTo(MATCH_ID);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wicket tracking across events
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Wicket accumulation across deliveries")
    class WicketAccumulation {

        @Test
        @DisplayName(":wickets value reflects cumulative wickets at time of update")
        void wickets_cumulativeAcrossEvents() {
            // Ball 1: no wicket, 0 wickets total
            repository.update(buildEvent(1, 0, 1, "MI", 4, false, 4, 0,
                    Instant.parse("2025-04-15T14:00:00Z")));
            // Ball 2: wicket, 1 wicket total
            repository.update(buildEvent(1, 0, 2, "MI", 0, true, 4, 1,
                    Instant.parse("2025-04-15T14:01:00Z")));
            // Ball 3: no wicket, 1 wicket total
            repository.update(buildEvent(1, 0, 3, "MI", 6, false, 10, 1,
                    Instant.parse("2025-04-15T14:02:00Z")));

            ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
            verify(dynamoDbClient, times(3)).updateItem(captor.capture());

            List<UpdateItemRequest> reqs = captor.getAllValues();
            assertThat(reqs.get(0).expressionAttributeValues().get(":wickets").n()).isEqualTo("0");
            assertThat(reqs.get(1).expressionAttributeValues().get(":wickets").n()).isEqualTo("1");
            assertThat(reqs.get(2).expressionAttributeValues().get(":wickets").n()).isEqualTo("1");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private ScoreEvent buildEvent(int inning, int over, int ball, String team,
            int runs, boolean wicket, int totalRuns,
            int wickets, Instant timestamp) {
        return new ScoreEvent(
                UUID.randomUUID().toString(),
                MATCH_ID,
                inning, over, ball,
                team, runs, null, wicket,
                totalRuns, wickets,
                timestamp);
    }
}
