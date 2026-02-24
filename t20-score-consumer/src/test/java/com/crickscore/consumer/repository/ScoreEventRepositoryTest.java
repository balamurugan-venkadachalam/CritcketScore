package com.crickscore.consumer.repository;

import com.crickscore.consumer.model.ScoreEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DynamoDbScoreEventRepository}.
 *
 * Verifies PutItem construction and idempotency re-throw behaviour.
 * Uses a mocked {@link DynamoDbClient} — no real AWS connection required.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ScoreEventRepositoryTest {

    @Mock
    private DynamoDbClient dynamoDbClient;

    private DynamoDbScoreEventRepository repository;

    private static final String TABLE = "t20-score-events";

    private static final ScoreEvent EVENT = new ScoreEvent(
            UUID.randomUUID().toString(),
            "IPL-2025-MI-CSK-001",
            1, 0, 1, "MI", 4, null, false, 4, 0,
            Instant.parse("2025-04-15T14:00:00Z"));

    @BeforeEach
    void setUp() {
        ScoreEventMapper mapper = new ScoreEventMapper();
        AppPropertiesDelegate delegate = mock(AppPropertiesDelegate.class);
        when(delegate.scoreEventsTable()).thenReturn(TABLE);
        repository = new DynamoDbScoreEventRepository(dynamoDbClient, mapper, delegate);
    }

    @Nested
    @DisplayName("save() — new event")
    class NewEvent {

        @Test
        @DisplayName("calls putItem once with the correct table name")
        void callsPutItemOnce() {
            repository.save(EVENT);
            verify(dynamoDbClient, times(1)).putItem(any(PutItemRequest.class));
        }

        @Test
        @DisplayName("PutItemRequest targets the configured table name")
        void putItemTargetsCorrectTable() {
            repository.save(EVENT);

            var captor = org.mockito.ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());
            assertThat(captor.getValue().tableName()).isEqualTo(TABLE);
        }

        @Test
        @DisplayName("PutItemRequest contains the conditional expression for idempotency")
        void putItemHasConditionalExpression() {
            repository.save(EVENT);

            var captor = org.mockito.ArgumentCaptor.forClass(PutItemRequest.class);
            verify(dynamoDbClient).putItem(captor.capture());
            assertThat(captor.getValue().conditionExpression())
                    .isEqualTo("attribute_not_exists(matchId)");
        }
    }

    @Nested
    @DisplayName("save() — duplicate event")
    class DuplicateEvent {

        @Test
        @DisplayName("re-throws ConditionalCheckFailedException when DynamoDB rejects a duplicate")
        void rethrowsConditionalCheckFailedException() {
            ConditionalCheckFailedException ddbEx = (ConditionalCheckFailedException) ConditionalCheckFailedException
                    .builder()
                    .message("The conditional request failed")
                    .build();

            when(dynamoDbClient.putItem(any(PutItemRequest.class))).thenThrow(ddbEx);

            assertThatThrownBy(() -> repository.save(EVENT))
                    .isInstanceOf(ConditionalCheckFailedException.class);
        }
    }

    // Static import for assertThat used in inner class
    private static <T> org.assertj.core.api.AbstractObjectAssert<?, T> assertThat(T actual) {
        return org.assertj.core.api.Assertions.assertThat(actual);
    }
}
