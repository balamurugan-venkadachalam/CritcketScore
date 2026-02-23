package com.crickscore.consumer.service;

import com.crickscore.consumer.model.ScoreEvent;
import com.crickscore.consumer.repository.LiveScoreRepository;
import com.crickscore.consumer.repository.ScoreEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the full processing pipeline for a single {@link ScoreEvent}.
 *
 * <h3>Processing sequence</h3>
 * <ol>
 * <li><b>Idempotency check</b> — if {@code eventId} was already processed,
 * log and return immediately without touching DynamoDB.</li>
 * <li><b>Event store write</b> — persist the ball delivery to
 * {@code t20-score-events} (PK: matchId, SK: eventSequence).</li>
 * <li><b>Materialized view update</b> — atomically update running totals in
 * {@code t20-live-scores} (totalRuns, wickets, currentOver, currentBall).</li>
 * </ol>
 *
 * <h3>Why this order?</h3>
 * The event store write is first because:
 * <ul>
 * <li>If the live score update fails after the event store write succeeds,
 * the event is safely stored and the retry (TASK-12) will re-process it.
 * The event store's conditional write ({@code attribute_not_exists}) will
 * reject the duplicate, and only the live score update will re-run.</li>
 * <li>This makes the pipeline crash-safe: partial failures are always
 * recoverable.</li>
 * </ul>
 *
 * <h3>Error handling</h3>
 * This service does NOT catch exceptions. Any uncaught exception propagates to
 * {@link com.crickscore.consumer.kafka.ScoreEventListener}, which will NOT
 * call {@code ack.acknowledge()}, causing the consumer to retry via
 * {@code @RetryableTopic} (TASK-12).
 */
@Slf4j
@Service
public class ScoreEventService {

    private final IdempotencyService idempotencyService;
    private final ScoreEventRepository scoreEventRepository;
    private final LiveScoreRepository liveScoreRepository;
    private final Timer processingTimer;
    private final Counter processedCounter;
    private final Counter duplicateCounter;

    @Autowired
    public ScoreEventService(
            IdempotencyService idempotencyService,
            ScoreEventRepository scoreEventRepository,
            LiveScoreRepository liveScoreRepository,
            MeterRegistry meterRegistry) {
        this.idempotencyService = idempotencyService;
        this.scoreEventRepository = scoreEventRepository;
        this.liveScoreRepository = liveScoreRepository;

        this.processingTimer = Timer.builder("score.event.processing.latency")
                .description("End-to-end processing latency per score event (idempotency + DynamoDB writes)")
                .publishPercentileHistogram()
                .register(meterRegistry);

        this.processedCounter = Counter.builder("score.events.processed.total")
                .description("Total score events successfully processed")
                .register(meterRegistry);

        this.duplicateCounter = Counter.builder("score.events.duplicate.total")
                .description("Total score events skipped as duplicates")
                .register(meterRegistry);
    }

    /**
     * Processes a single {@link ScoreEvent} through the full pipeline.
     *
     * @param event   the deserialized score event from Kafka.
     * @param traceId the OpenTelemetry trace ID propagated from the Kafka header,
     *                used for log correlation.
     */
    public void process(ScoreEvent event, String traceId) {
        log.debug("Processing score event: matchId={}, eventId={}, sequence={}, traceId={}",
                event.matchId(), event.eventId(), event.eventSequence(), traceId);

        Timer.Sample sample = Timer.start();

        try {
            // Step 1 — Idempotency check
            if (idempotencyService.isDuplicate(event.eventId())) {
                duplicateCounter.increment();
                log.info("Skipping duplicate event: eventId={}, matchId={}",
                        event.eventId(), event.matchId());
                return;
            }

            // Step 2 — Persist to event store (t20-score-events)
            scoreEventRepository.save(event);
            log.debug("Event persisted: matchId={}, sequence={}", event.matchId(), event.eventSequence());

            // Step 3 — Update materialized live score view (t20-live-scores)
            liveScoreRepository.update(event);
            log.debug("Live score updated: matchId={}, totalRuns={}, wickets={}",
                    event.matchId(), event.totalRuns(), event.wickets());

            processedCounter.increment();
            log.info("Score event processed: matchId={}, sequence={}, totalRuns={}, wickets={}, traceId={}",
                    event.matchId(), event.eventSequence(), event.totalRuns(), event.wickets(), traceId);

        } finally {
            sample.stop(processingTimer);
        }
    }
}
