package com.crickscore.consumer.repository;

import com.crickscore.consumer.model.ScoreEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;

/**
 * DynamoDB implementation of {@link ScoreEventRepository}.
 *
 * <h3>Table: {@code t20-score-events}</h3>
 * <ul>
 * <li>PK: {@code matchId} (String)</li>
 * <li>SK: {@code eventSequence} → {@code "inning#over#ball"} (String)</li>
 * </ul>
 *
 * <h3>Idempotency via conditional write</h3>
 * The {@code ConditionExpression attribute_not_exists(matchId)} ensures that
 * if a record with the same PK+SK already exists, DynamoDB throws
 * {@link ConditionalCheckFailedException} instead of overwriting.
 * The caller ({@link com.crickscore.consumer.service.ScoreEventService}) treats
 * a {@code ConditionalCheckFailedException} as "already processed" and skips
 * without raising an error — guaranteeing at-most-once DynamoDB writes for any
 * given ball delivery.
 *
 * <h3>Attributes stored</h3>
 * All fields from {@link ScoreEvent} are written as DynamoDB
 * {@link AttributeValue}s,
 * using the mapper {@link ScoreEventMapper}.
 *
 * <p>
 * Active on all non-test profiles. {@link NoOpScoreEventRepository} is used
 * during {@code local} and {@code test} contexts only while TASK-11 was
 * pending.
 * This bean is now {@code @Primary} and replaces the NoOp stub.
 */
@Slf4j
@Repository
@Primary
@Profile("!unit-test")
public class DynamoDbScoreEventRepository implements ScoreEventRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final ScoreEventMapper mapper;

    public DynamoDbScoreEventRepository(
            DynamoDbClient dynamoDbClient,
            ScoreEventMapper mapper,
            AppPropertiesDelegate appProperties) {
        this.dynamoDbClient = dynamoDbClient;
        this.mapper = mapper;
        this.tableName = appProperties.scoreEventsTable();
    }

    /**
     * Persists a {@link ScoreEvent} to the {@code t20-score-events} event store.
     *
     * <p>
     * Uses a conditional write ({@code attribute_not_exists(matchId)}) to ensure
     * idempotency — the same event cannot be written twice.
     *
     * @param event the ball delivery event to persist.
     * @throws ConditionalCheckFailedException if the event was already stored
     *                                         (treated as a duplicate by the
     *                                         caller, not an application error).
     */
    @Override
    public void save(ScoreEvent event) {
        Map<String, AttributeValue> item = mapper.toEventStoreItem(event);

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                // Idempotency guard: fail if matchId PK already exists for this SK
                .conditionExpression("attribute_not_exists(matchId)")
                .build();

        try {
            dynamoDbClient.putItem(request);
            log.debug("Saved score event: matchId={}, sequence={}, eventId={}",
                    event.matchId(), event.eventSequence(), event.eventId());
        } catch (ConditionalCheckFailedException e) {
            // Duplicate write — already processed. Re-throw so caller can skip cleanly.
            log.warn("Duplicate score event rejected by DynamoDB conditional write: " +
                    "matchId={}, sequence={}, eventId={}",
                    event.matchId(), event.eventSequence(), event.eventId());
            throw e;
        }
    }
}
