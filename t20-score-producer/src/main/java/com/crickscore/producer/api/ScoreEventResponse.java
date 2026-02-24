package com.crickscore.producer.api;

import lombok.Builder;

/**
 * Response body returned by {@code POST /api/v1/scores} on success.
 *
 * <p>
 * Returning the {@code eventId} lets the calling client track the event
 * through downstream systems (consumer logs, DynamoDB, traces all carry the
 * same ID).
 */
@Builder
public record ScoreEventResponse(

        /** Auto-generated UUID that uniquely identifies this ball delivery. */
        String eventId,

        /** The match to which this event belongs. */
        String matchId,

        /** Human-readable confirmation message. */
        String message

) {

    /**
     * Convenience factory for the happy-path 202 Accepted case.
     */
    public static ScoreEventResponse accepted(String eventId, String matchId) {
        return ScoreEventResponse.builder()
                .eventId(eventId)
                .matchId(matchId)
                .message("Score event accepted for publishing")
                .build();
    }
}
