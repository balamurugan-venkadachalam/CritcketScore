package com.crickscore.consumer.repository;

import com.crickscore.consumer.model.ScoreEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.Map;

/**
 * DynamoDB implementation of {@link LiveScoreRepository}.
 *
 * <h3>Table: {@code t20-live-scores}</h3>
 * <ul>
 * <li>PK: {@code matchId} (String)</li>
 * </ul>
 * One item per match. Holds running aggregates updated atomically on each ball.
 *
 * <h3>Why a materialized view?</h3>
 * Querying all balls in {@code t20-score-events} and summing runs on every
 * score-page request would be O(n) DynamoDB reads (n = balls bowled). With
 * millions of viewers this is prohibitive. A single {@code GetItem} on
 * {@code t20-live-scores} is O(1) regardless of match progress.
 *
 * <h3>Update expression</h3>
 * {@code SET} expressions are used for all fields because DynamoDB guarantees
 * atomic execution of a single {@code UpdateItem} operation. Because
 * {@code AckMode.RECORD} + single thread per partition in the consumer ensures
 * balls are written strictly in order, we never have a race where Ball N+1
 * overwrites Ball N's values.
 *
 * <h3>Idempotency</h3>
 * The event store write ({@link DynamoDbScoreEventRepository}) acts as the
 * idempotency gate — if a duplicate is detected there, this update is never
 * called. No additional conditional expression is needed here.
 */
@Slf4j
@Repository
@Primary
@Profile("!unit-test")
public class DynamoDbLiveScoreRepository implements LiveScoreRepository {

    private static final String UPDATE_EXPRESSION = "SET totalRuns = :totalRuns, " +
            "    wickets = :wickets, " +
            "    currentInning = :inning, " +
            "    currentOver = :over, " +
            "    currentBall = :ball, " +
            "    lastBallRuns = :runs, " +
            "    isWicket = :wicket, " +
            "    lastUpdated = :lastUpdated, " +
            "    matchId = if_not_exists(matchId, :matchId)";

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final ScoreEventMapper mapper;

    public DynamoDbLiveScoreRepository(
            DynamoDbClient dynamoDbClient,
            ScoreEventMapper mapper,
            AppPropertiesDelegate appProperties) {
        this.dynamoDbClient = dynamoDbClient;
        this.mapper = mapper;
        this.tableName = appProperties.liveScoresTable();
    }

    /**
     * Atomically updates the live score for the match, reflecting the latest
     * ball delivery in {@code event}.
     *
     * @param event the ball delivery to apply.
     */
    @Override
    public void update(ScoreEvent event) {
        Map<String, AttributeValue> key = Map.of(
                "matchId", AttributeValue.fromS(event.matchId()));

        Map<String, AttributeValue> expressionValues = mapper.toLiveScoreExpressionValues(event);

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression(UPDATE_EXPRESSION)
                .expressionAttributeValues(expressionValues)
                .build();

        dynamoDbClient.updateItem(request);

        log.debug("Updated live score: matchId={}, inning={}, over={}, ball={}, totalRuns={}, wickets={}",
                event.matchId(), event.inning(), event.over(), event.ball(),
                event.totalRuns(), event.wickets());
    }
}
