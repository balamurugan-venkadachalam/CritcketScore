package com.crickscore.consumer.repository;

import com.crickscore.consumer.model.ScoreEvent;

/**
 * Port for updating the live score materialized view in the
 * {@code t20-live-scores}
 * DynamoDB table.
 *
 * <p>
 * TASK-10: {@link NoOpLiveScoreRepository} (stub — logs only).
 * <p>
 * TASK-11: DynamoDB implementation uses {@code UpdateExpression} with atomic
 * {@code ADD totalRuns :runs} and conditional {@code SET} for over/ball.
 *
 * <p>
 * <b>Live score item key:</b>
 * <ul>
 * <li>PK: {@code matchId}</li>
 * </ul>
 * The item holds running aggregates: {@code totalRuns}, {@code wickets},
 * {@code currentOver}, {@code currentBall}, {@code lastUpdated}.
 *
 * <p>
 * <b>Why a materialized view?</b> Querying all balls for a match to compute
 * the score on-demand costs O(n) DynamoDB reads (n = balls bowled). At IPL
 * scale
 * with millions of viewers, this is prohibitive. A single {@code GetItem}
 * on the live score table is O(1) regardless of balls bowled.
 */
public interface LiveScoreRepository {

    /**
     * Atomically updates the live score for the match identified by
     * {@link ScoreEvent#matchId()} to reflect the given ball delivery.
     *
     * @param event the ball delivery to apply.
     */
    void update(ScoreEvent event);
}
