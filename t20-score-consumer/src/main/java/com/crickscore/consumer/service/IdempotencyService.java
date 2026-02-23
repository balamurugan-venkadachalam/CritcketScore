package com.crickscore.consumer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guards against duplicate event processing using an {@code eventId} uniqueness
 * check.
 *
 * <h3>Current implementation (TASK-10)</h3>
 * Uses an in-memory {@link ConcurrentHashMap}-backed set. This is sufficient
 * for
 * unit tests and local development but does NOT survive pod restarts or work
 * across
 * multiple consumer instances.
 *
 * <h3>Production implementation (TASK-11)</h3>
 * TASK-11 replaces this with a DynamoDB conditional write on the
 * {@code t20-score-events}
 * table using
 * {@code attribute_not_exists(matchId) AND attribute_not_exists(eventSequence)}.
 * That check is atomic, durable, and works across all consumer pods. If the
 * condition
 * fails, DynamoDB throws {@code ConditionalCheckFailedException}, which the
 * consumer
 * treats as "already processed" and acknowledges without error.
 *
 * <h3>Why not Redis SETNX?</h3>
 * DynamoDB is already in the stack. Adding Redis for idempotency would
 * introduce an
 * additional managed service with its own failover and cost. The DynamoDB
 * conditional
 * write is atomic by design and has no extra infrastructure cost.
 */
@Slf4j
@Service
public class IdempotencyService {

    /**
     * In-memory seen-set. Thread-safe; backed by a concurrent hash map.
     *
     * <p>
     * <b>Limitation:</b> cleared on restart; not shared across pods.
     * TASK-11 replaces this with durable DynamoDB-based idempotency.
     */
    private final Set<String> seenEventIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Returns {@code true} if {@code eventId} has already been processed
     * (i.e., is a duplicate), {@code false} if it is new.
     *
     * <p>
     * On first call for a given {@code eventId}, marks it as seen and returns
     * {@code false}.
     * Subsequent calls for the same {@code eventId} return {@code true}.
     *
     * @param eventId the unique event identifier from
     *                {@link com.crickscore.consumer.model.ScoreEvent#eventId()}.
     * @return {@code true} if this event was already processed.
     */
    public boolean isDuplicate(String eventId) {
        boolean isNew = seenEventIds.add(eventId); // add() returns false if already present
        if (!isNew) {
            log.warn("Duplicate event detected and skipped: eventId={}", eventId);
        }
        return !isNew;
    }
}
