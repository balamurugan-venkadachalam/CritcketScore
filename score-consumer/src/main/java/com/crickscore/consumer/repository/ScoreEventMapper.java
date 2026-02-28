package com.crickscore.consumer.repository;

import com.crickscore.consumer.model.ScoreEvent;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps {@link ScoreEvent} instances to DynamoDB {@link AttributeValue} maps.
 *
 * <p>
 * Centralising attribute mapping here keeps it testable in isolation and
 * prevents DynamoDB attribute name strings from scattering across repositories.
 *
 * <h3>Attribute name conventions</h3>
 * <ul>
 * <li>Strings → {@link AttributeValue#fromS(String)}</li>
 * <li>Numbers → {@link AttributeValue#fromN(String)} (DynamoDB number
 * type)</li>
 * <li>Booleans → {@link AttributeValue#fromBool(Boolean)}</li>
 * </ul>
 */
@Component
public class ScoreEventMapper {

    /**
     * Converts a {@link ScoreEvent} to a DynamoDB item map for
     * {@code PutItem} into the {@code t20-score-events} event store.
     *
     * <h3>Key attributes</h3>
     * <ul>
     * <li>{@code matchId} — partition key</li>
     * <li>{@code eventSequence} — sort key, value of
     * {@link ScoreEvent#eventSequence()}</li>
     * </ul>
     *
     * @param event the score event to convert.
     * @return a map of DynamoDB attribute names to {@link AttributeValue}s.
     */
    public Map<String, AttributeValue> toEventStoreItem(ScoreEvent event) {
        Map<String, AttributeValue> item = new HashMap<>();

        // ── Keys ──────────────────────────────────────────────────────────────
        item.put("matchId", AttributeValue.fromS(event.matchId()));
        item.put("eventSequence", AttributeValue.fromS(event.eventSequence()));

        // ── Identification ────────────────────────────────────────────────────
        item.put("eventId", AttributeValue.fromS(event.eventId()));

        // ── Ball data ─────────────────────────────────────────────────────────
        item.put("inning", AttributeValue.fromN(String.valueOf(event.inning())));
        item.put("over", AttributeValue.fromN(String.valueOf(event.over())));
        item.put("ball", AttributeValue.fromN(String.valueOf(event.ball())));
        item.put("team", AttributeValue.fromS(event.team()));
        item.put("runs", AttributeValue.fromN(String.valueOf(event.runs())));
        item.put("wicket", AttributeValue.fromBool(event.wicket()));

        if (event.extras() != null) {
            item.put("extras", AttributeValue.fromN(String.valueOf(event.extras())));
        }

        // ── Match running totals ──────────────────────────────────────────────
        item.put("totalRuns", AttributeValue.fromN(String.valueOf(event.totalRuns())));
        item.put("wickets", AttributeValue.fromN(String.valueOf(event.wickets())));

        // ── Timestamp ─────────────────────────────────────────────────────────
        item.put("timestamp", AttributeValue.fromS(event.timestamp().toString()));
        item.put("ttl", AttributeValue.fromN(String.valueOf(
                event.timestamp().plusSeconds(90L * 24 * 3600).getEpochSecond()))); // 90-day TTL

        return item;
    }

    /**
     * Builds the expression attribute values for an {@code UpdateItem} call
     * on the {@code t20-live-scores} materialized view.
     *
     * <p>
     * The keys ({@code :totalRuns}, {@code :wickets}, etc.) match exactly the
     * placeholders in {@link DynamoDbLiveScoreRepository#UPDATE_EXPRESSION}.
     *
     * @param event the ball delivery to reflect in the live score.
     * @return expression attribute value map.
     */
    public Map<String, AttributeValue> toLiveScoreExpressionValues(ScoreEvent event) {
        Map<String, AttributeValue> values = new HashMap<>();

        values.put(":matchId", AttributeValue.fromS(event.matchId()));
        values.put(":totalRuns", AttributeValue.fromN(String.valueOf(event.totalRuns())));
        values.put(":wickets", AttributeValue.fromN(String.valueOf(event.wickets())));
        values.put(":inning", AttributeValue.fromN(String.valueOf(event.inning())));
        values.put(":over", AttributeValue.fromN(String.valueOf(event.over())));
        values.put(":ball", AttributeValue.fromN(String.valueOf(event.ball())));
        values.put(":runs", AttributeValue.fromN(String.valueOf(event.runs())));
        values.put(":wicket", AttributeValue.fromBool(event.wicket()));
        values.put(":lastUpdated", AttributeValue.fromS(event.timestamp().toString()));

        return values;
    }
}
