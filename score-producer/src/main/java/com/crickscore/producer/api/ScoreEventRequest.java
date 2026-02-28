package com.crickscore.producer.api;

import com.crickscore.producer.model.ScoreEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.Instant;

/**
 * REST request DTO for {@code POST /api/v1/scores}.
 *
 * <p>
 * Fields deliberately mirror {@link ScoreEvent} minus {@code eventId} and
 * {@code timestamp} — those are generated server-side so callers can't supply
 * them directly (prevents replay or timestamp skew attacks).
 *
 * <h3>Validation</h3>
 * All validation constraints are declared here so that validation errors
 * surface
 * as meaningful 400 responses (handled by {@link GlobalExceptionHandler})
 * before
 * any business logic runs.
 *
 * <h3>Conversion</h3>
 * Use {@link #toScoreEvent()} to create a fully-populated, immutable domain
 * event.
 * The factory calls {@link ScoreEvent#withDefaults(ScoreEvent)} which
 * auto-generates
 * {@code eventId} and {@code timestamp}.
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreEventRequest(

        /**
         * Unique match identifier, e.g. {@code "IPL-2025-MI-CSK-001"}.
         * Used as the Kafka partition key — must be stable for a given match.
         */
        @NotBlank(message = "matchId must not be blank") String matchId,

        /**
         * Innings number: 1 (first innings) or 2 (second innings).
         */
        @NotNull(message = "inning must not be null") @Min(value = 1, message = "inning must be 1 or 2") @Max(value = 2, message = "inning must be 1 or 2") Integer inning,

        /**
         * Over number within the innings, 0-indexed (0–19 for T20).
         */
        @NotNull(message = "over must not be null") @Min(value = 0, message = "over must be between 0 and 19") @Max(value = 19, message = "over must be between 0 and 19") Integer over,

        /**
         * Ball number within the over, 1-indexed (1–6; extra deliveries may exceed 6).
         */
        @NotNull(message = "ball must not be null") @Min(value = 1, message = "ball must be at least 1") @Max(value = 9, message = "ball must be at most 9 (including extras)") Integer ball,

        /**
         * Batting team code, e.g. {@code "MI"}, {@code "CSK"}.
         */
        @NotBlank(message = "team must not be blank") String team,

        /**
         * Runs scored from this delivery (0–6 for a single legal ball).
         */
        @NotNull(message = "runs must not be null") @Min(value = 0, message = "runs must be >= 0") @Max(value = 6, message = "runs must be <= 6") Integer runs,

        /**
         * Extras (wides, no-balls, byes, leg-byes). Null if none.
         */
        @Min(value = 0, message = "extras must be >= 0") Integer extras,

        /**
         * Whether a wicket fell on this delivery.
         */
        @NotNull(message = "wicket must not be null") Boolean wicket,

        /**
         * Cumulative team total at the end of this delivery.
         */
        @NotNull(message = "totalRuns must not be null") @Min(value = 0, message = "totalRuns must be >= 0") Integer totalRuns,

        /**
         * Cumulative wickets at the end of this delivery (0–10).
         */
        @NotNull(message = "wickets must not be null") @Min(value = 0, message = "wickets must be >= 0") @Max(value = 10, message = "wickets must be <= 10") Integer wickets

) {

    /**
     * Converts this request DTO to a fully-populated {@link ScoreEvent}.
     *
     * <p>
     * {@code eventId} is auto-generated (UUID v4) and {@code timestamp} is set
     * to the current UTC instant, both via {@link ScoreEvent#withDefaults}.
     *
     * @return a new immutable {@link ScoreEvent} ready to publish.
     */
    public ScoreEvent toScoreEvent() {
        ScoreEvent prototype = ScoreEvent.builder()
                .matchId(matchId)
                .inning(inning)
                .over(over)
                .ball(ball)
                .team(team)
                .runs(runs)
                .extras(extras)
                .wicket(wicket)
                .totalRuns(totalRuns)
                .wickets(wickets)
                .timestamp(Instant.now())
                .build();
        return ScoreEvent.withDefaults(prototype);
    }
}
