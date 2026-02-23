package com.crickscore.consumer.repository;

import com.crickscore.consumer.model.ScoreEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/**
 * No-op stub implementation of {@link LiveScoreRepository}.
 *
 * <p>
 * Active on {@code local} and {@code test} profiles only.
 * TASK-11 adds the real {@code DynamoDbLiveScoreRepository}.
 */
@Slf4j
@Repository
@Profile({ "local", "test" })
public class NoOpLiveScoreRepository implements LiveScoreRepository {

    @Override
    public void update(ScoreEvent event) {
        log.info("[NoOp] Would update t20-live-scores: matchId={}, totalRuns={}, wickets={}, over={}, ball={}",
                event.matchId(), event.totalRuns(), event.wickets(), event.over(), event.ball());
    }
}
