package com.crickscore.consumer.repository;

import com.crickscore.consumer.model.ScoreEvent;

/**
 * Port for writing score events to the {@code t20-score-events} DynamoDB table.
 *
 * <p>
 * TASK-10: {@link NoOpScoreEventRepository} (stub — logs only, no real write).
 * <p>
 * TASK-11: DynamoDB implementation replaces the stub.
 *
 * <p>
 * <b>DynamoDB key design:</b>
 * <ul>
 * <li>PK: {@code matchId}</li>
 * <li>SK: {@link ScoreEvent#eventSequence()} → {@code "inning#over#ball"}</li>
 * </ul>
 * A {@code attribute_not_exists(matchId)} conditional expression prevents
 * overwriting existing events, providing idempotent writes.
 */
public interface ScoreEventRepository {

    /**
     * Persists a {@link ScoreEvent} to the event store.
     *
     * @param event the event to persist.
     * @throws org.springframework.dao.DataIntegrityViolationException (or AWS SDK
     *                                                                 equivalent)
     *                                                                 if an item
     *                                                                 with the same
     *                                                                 PK+SK already
     *                                                                 exists and
     *                                                                 the
     *                                                                 implementation
     *                                                                 uses
     *                                                                 conditional
     *                                                                 writes.
     */
    void save(ScoreEvent event);
}
