package com.crickscore.producer.api;

import com.crickscore.producer.kafka.ScoreEventProducerService;
import com.crickscore.producer.model.ScoreEvent;
import io.micrometer.tracing.Tracer;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the score ingestion endpoint.
 *
 * <h3>Endpoint</h3>
 * {@code POST /api/v1/scores} — accepts a {@link ScoreEventRequest}, validates
 * it, converts it to a {@link ScoreEvent}, publishes it to Kafka
 * asynchronously,
 * and returns {@code 202 Accepted} immediately.
 *
 * <h3>Why 202 and not 201?</h3>
 * The broker acknowledgement is asynchronous (acks = all). Returning 202
 * signals
 * that the request has been <em>accepted for processing</em> but the event may
 * not yet be durably stored. The caller should use the {@code eventId} in the
 * response to correlate downstream confirmations.
 *
 * <h3>Error responses</h3>
 * <ul>
 * <li>{@code 400} — Bean Validation failed; field errors returned as JSON.</li>
 * <li>{@code 503} — Kafka send failed immediately (e.g. serialisation
 * error).</li>
 * <li>{@code 500} — Unexpected error.</li>
 * </ul>
 * All error shapes are defined in {@link GlobalExceptionHandler}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/scores")
public class ScoreEventController {

    private final ScoreEventProducerService producerService;
    private final Tracer tracer;

    @Autowired
    public ScoreEventController(ScoreEventProducerService producerService,
            Tracer tracer) {
        this.producerService = producerService;
        this.tracer = tracer;
    }

    /**
     * Accepts a single ball-delivery event and publishes it to Kafka.
     *
     * @param request validated request body.
     * @return {@code 202 Accepted} with {@link ScoreEventResponse} containing
     *         the auto-generated {@code eventId}.
     */
    @PostMapping
    public ResponseEntity<ScoreEventResponse> publish(
            @Valid @RequestBody ScoreEventRequest request) {

        ScoreEvent event = request.toScoreEvent();

        String traceId = resolveTraceId();

        log.info("Received score event: matchId={}, sequence={}, eventId={}, traceId={}",
                event.matchId(), event.eventSequence(), event.eventId(), traceId);

        // Fire-and-forget — failures are handled asynchronously by
        // ProducerCallbackHandler
        producerService.send(event, traceId);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ScoreEventResponse.accepted(event.eventId(), event.matchId()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extracts the current trace ID from Micrometer Tracing.
     * Returns an empty string if tracing is not active (e.g. in unit tests
     * without a real tracer).
     */
    private String resolveTraceId() {
        try {
            var span = tracer.currentSpan();
            return span != null ? span.context().traceId() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
