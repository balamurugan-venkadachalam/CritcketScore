package com.crickscore.producer.kafka;

import com.crickscore.producer.model.ScoreEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

/**
 * Handles Kafka produce callbacks — success and failure — emitting structured
 * log lines and Micrometer counters.
 *
 * <p>
 * This component is invoked by {@link ScoreEventProducerService} in the
 * {@code CompletableFuture.whenComplete()} handler, which runs on the Kafka
 * sender thread pool. Keep this implementation fast and non-blocking — never
 * call blocking I/O here.
 *
 * <h3>Metrics emitted</h3>
 * <table>
 * <tr>
 * <th>Metric</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>{@code score.events.sent.total}</td>
 * <td>Incremented on every broker ACK</td>
 * </tr>
 * <tr>
 * <td>{@code score.events.failed.total}</td>
 * <td>Incremented on every produce failure</td>
 * </tr>
 * </table>
 */
@Slf4j
@Component
public class ProducerCallbackHandler {

    private final Counter sentCounter;
    private final Counter failedCounter;

    @Autowired
    public ProducerCallbackHandler(MeterRegistry meterRegistry) {
        this.sentCounter = Counter.builder("score.events.sent.total")
                .description("Total score events successfully acknowledged by Kafka broker")
                .register(meterRegistry);

        this.failedCounter = Counter.builder("score.events.failed.total")
                .description("Total score events that failed to be acknowledged by Kafka broker")
                .register(meterRegistry);
    }

    /**
     * Called when the Kafka broker has acknowledged the record.
     *
     * @param event  the original {@link ScoreEvent} that was sent.
     * @param result the broker's acknowledgement, containing topic, partition, and
     *               offset.
     */
    public void onSuccess(ScoreEvent event, SendResult<String, String> result) {
        sentCounter.increment();

        if (log.isDebugEnabled()) {
            log.debug(
                    "Score event produced successfully: " +
                            "matchId={} eventId={} sequence={} topic={} partition={} offset={}",
                    event.matchId(),
                    event.eventId(),
                    event.eventSequence(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        }
    }

    /**
     * Called when the Kafka producer could not deliver the record after exhausting
     * retries
     * (or hitting {@code delivery.timeout.ms}).
     *
     * <p>
     * At this point the message is lost — the producer does <b>not</b> route it to
     * a
     * retry/DLT topic automatically. The caller (HTTP request handler or simulator)
     * is
     * responsible for responding with a 503 and letting the client retry.
     *
     * @param event the original {@link ScoreEvent} that failed.
     * @param ex    the root cause exception.
     */
    public void onFailure(ScoreEvent event, Throwable ex) {
        failedCounter.increment();

        log.error(
                "Score event produce FAILED: matchId={} eventId={} sequence={} error={}",
                event.matchId(),
                event.eventId(),
                event.eventSequence(),
                ex.getMessage(),
                ex);
    }
}
