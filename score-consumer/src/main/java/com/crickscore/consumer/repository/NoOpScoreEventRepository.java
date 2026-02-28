package com.crickscore.consumer.repository;

import com.crickscore.consumer.model.ScoreEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * No-op stub implementation of {@link ScoreEventRepository}.
 *
 * <p>
 * Active on {@code local} and {@code test} profiles only. Logs the event
 * instead of writing to DynamoDB so the full Kafka pipeline can be exercised
 * without DynamoDB available.
 *
 * <p>
 * TASK-11 adds the real {@code DynamoDbScoreEventRepository} which takes
 * over on all profiles.
 */
@Slf4j
@Repository
@Profile("unit-test")
public class NoOpScoreEventRepository implements ScoreEventRepository {

    @Override
    public void save(ScoreEvent event) {
        log.info("[NoOp] Would write to t20-score-events: matchId={}, sequence={}, eventId={}",
                event.matchId(), event.eventSequence(), event.eventId());
    }
}
