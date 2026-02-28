package com.crickscore.consumer.service;

/**
 * Port for dispatching dead-letter notifications to the ops team.
 *
 * <p>
 * TASK-12: {@link NoOpDeadLetterNotificationService} (stub — logs only).
 * <p>
 * TASK-14/Infra: AWS SNS implementation sends a real alert with event payload,
 * error type, timestamp, and partition/offset info.
 */
public interface DeadLetterNotificationService {

    /**
     * Sends a notification for a dead-lettered event.
     *
     * @param matchId      the match the failed event belongs to.
     * @param partition    the DLT partition the record landed on.
     * @param offset       the DLT partition offset.
     * @param payload      the raw JSON payload (may be malformed).
     * @param errorFqcn    fully-qualified class name of the root exception.
     * @param errorMessage human-readable error message.
     */
    void notify(String matchId, int partition, long offset,
            String payload, String errorFqcn, String errorMessage);
}
