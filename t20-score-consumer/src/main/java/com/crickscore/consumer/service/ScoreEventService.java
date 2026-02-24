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
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/**
 * Orchestrates the full processing pipeline for a single {@link ScoreEvent}.
 *
 * <h3>Processing sequence</h3>
 * <ol>
 * <li><b>Event store write</b> — {@code PutItem} to {@code t20-score-events}
 * with {@code attribute_not_exists(matchId)} conditional expression.
 * If the item already exists (duplicate), DynamoDB throws
 * {@link ConditionalCheckFailedException} — caught here as a clean
 * duplicate skip, no retry triggered.</li>
 * <li><b>Materialized view update</b> — {@code UpdateItem} to
 * {@code t20-live-scores} (totalRuns, wickets, currentOver, currentBall).
 * Only reached on new events.</li>
 * </ol>
 *
 * <h3>Idempotency design (TASK-11)</h3>
 * The DynamoDB conditional write in
 * {@link ScoreEventRepository#save(ScoreEvent)}
 * is the single idempotency gate. The in-memory {@code IdempotencyService} stub
 * from TASK-10 has been removed in favour of this durable approach:
 * <ul>
 * <li><b>Why DynamoDB over Redis?</b> Redis requires a separate dependency and
 * connection management. DynamoDB conditional writes are inherently atomic
 * and survive pod restarts — no warm-up period needed.</li>
 * <li>Duplicate events are common during Kafka at-least-once delivery (consumer
 * restart before offset commit). The conditional write silently discards
 * them without impacting the live score view.</li>
 * </ul>
 *
 * <h3>Why this write order?</h3>
 * Event store first, live score second:
 * <ul>
 * <li>If the live score update fails <em>after</em> the event store write,
 * the retry (via {@code @RetryableTopic}) will re-attempt. The conditional
 * write will throw {@link ConditionalCheckFailedException} (duplicate),
 * so only the live score update re-runs — crash safe.</li>
 * </ul>
 *
 * <h3>Error propagation</h3>
 * All exceptions <em>except</em> {@link ConditionalCheckFailedException}
 * propagate
 * to {@link com.crickscore.consumer.kafka.ScoreEventListener}, which withholds
 * ACK and lets {@code @RetryableTopic} route to retry topics.
 */
@Slf4j
@Service
public class ScoreEventService {

        private final ScoreEventRepository scoreEventRepository;
        private final LiveScoreRepository liveScoreRepository;
        private final Timer processingTimer;
        private final Counter processedCounter;
        private final Counter duplicateCounter;

        @Autowired
        public ScoreEventService(
                        ScoreEventRepository scoreEventRepository,
                        LiveScoreRepository liveScoreRepository,
                        MeterRegistry meterRegistry) {
                this.scoreEventRepository = scoreEventRepository;
                this.liveScoreRepository = liveScoreRepository;

                this.processingTimer = Timer.builder("score.event.processing.latency")
                                .description("End-to-end processing latency per score event (DynamoDB writes)")
                                .publishPercentileHistogram()
                                .register(meterRegistry);

                this.processedCounter = Counter.builder("score.events.processed.total")
                                .description("Total score events successfully processed")
                                .register(meterRegistry);

                this.duplicateCounter = Counter.builder("score.events.duplicate.total")
                                .description("Total score events skipped as duplicates (DynamoDB conditional check)")
                                .register(meterRegistry);
        }

        /**
         * Processes a single {@link ScoreEvent} through the full pipeline.
         *
         * @param event   the deserialized score event from Kafka.
         * @param traceId the OpenTelemetry trace ID propagated from the Kafka header.
         */
        public void process(ScoreEvent event, String traceId) {
                log.debug("Processing score event: matchId={}, eventId={}, sequence={}, traceId={}",
                                event.matchId(), event.eventId(), event.eventSequence(), traceId);

                Timer.Sample sample = Timer.start();

                try {
                        // ── Step 1: Persist to event store (idempotency gate) ─────────────
                        try {
                                scoreEventRepository.save(event);
                                log.debug("Event persisted: matchId={}, sequence={}", event.matchId(),
                                                event.eventSequence());
                        } catch (ConditionalCheckFailedException e) {
                                // Duplicate: DynamoDB conditional write rejected — event already stored.
                                // This is NOT an error; skip cleanly without retrying.
                                duplicateCounter.increment();
                                log.info("Duplicate event skipped (DynamoDB conditional check): " +
                                                "matchId={}, sequence={}, eventId={}",
                                                event.matchId(), event.eventSequence(), event.eventId());
                                return;
                        }

                        // ── Step 2: Update materialized live score view ───────────────────
                        liveScoreRepository.update(event);
                        log.debug("Live score updated: matchId={}, totalRuns={}, wickets={}",
                                        event.matchId(), event.totalRuns(), event.wickets());

                        processedCounter.increment();
                        log.info("Score event processed: matchId={}, sequence={}, totalRuns={}, wickets={}, traceId={}",
                                        event.matchId(), event.eventSequence(), event.totalRuns(), event.wickets(),
                                        traceId);

                } finally {
                        sample.stop(processingTimer);
                }
        }
}
