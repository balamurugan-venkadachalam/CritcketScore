package com.crickscore.producer.kafka;

import com.crickscore.producer.model.ScoreEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for serialising and publishing {@link ScoreEvent}
 * instances to Kafka.
 *
 * <h3>Routing strategy</h3>
 * <ul>
 * <li><b>Topic:</b> {@code t20-match-scores} (configurable via
 * {@code app.kafka.topic.score-events}).</li>
 * <li><b>Partition key:</b> {@code matchId} — guarantees strict per-match
 * ordering because
 * Kafka routes all records with the same key to the same partition and a single
 * consumer
 * thread services each partition.</li>
 * </ul>
 *
 * <h3>Observability headers</h3>
 * Every produced record carries:
 * <ul>
 * <li>{@code X-Trace-Id} — the active Micrometer/OTel trace ID, enabling
 * end-to-end
 * correlation across HTTP → Kafka → consumer → DynamoDB.</li>
 * <li>{@code X-Match-Id} — redundant copy of the partition key for easy
 * filtering in
 * Kafka UI tools without needing to decode the record key.</li>
 * <li>{@code X-Event-Id} — idempotency key, mirrors the {@code eventId} field
 * so
 * consumer-side deduplication can happen at the header level without
 * deserialising the payload.</li>
 * </ul>
 *
 * <h3>Error handling</h3>
 * Serialisation failures throw {@link ScoreEventSerializationException}
 * immediately (synchronous)
 * and are never sent to Kafka. Network/broker failures surface asynchronously
 * in the returned
 * {@link CompletableFuture} and are logged by {@link ProducerCallbackHandler}.
 */
@Slf4j
@Service
public class ScoreEventProducerService {

    static final String HEADER_TRACE_ID = "X-Trace-Id";
    static final String HEADER_MATCH_ID = "X-Match-Id";
    static final String HEADER_EVENT_ID = "X-Event-Id";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ProducerCallbackHandler callbackHandler;
    private final TopicResolver topicResolver;
    private final Timer sendLatencyTimer;

    @Autowired
    public ScoreEventProducerService(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            ProducerCallbackHandler callbackHandler,
            TopicResolver topicResolver,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.callbackHandler = callbackHandler;
        this.topicResolver = topicResolver;
        this.sendLatencyTimer = Timer.builder("score.event.send.latency")
                .description("End-to-end latency from produce() call to broker ACK")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    /**
     * Serialises {@code event} to JSON and publishes it to the main score-events
     * topic.
     *
     * @param event   the fully-populated {@link ScoreEvent} (must have non-null
     *                {@code eventId},
     *                {@code matchId}, and {@code timestamp}).
     * @param traceId the active OpenTelemetry trace ID; written to the
     *                {@code X-Trace-Id} header.
     * @return a {@link CompletableFuture} that completes with the broker's
     *         {@link SendResult}
     *         (containing offset, partition, and topic) or fails with a Kafka
     *         exception.
     * @throws ScoreEventSerializationException if Jackson cannot serialise
     *                                          {@code event}.
     */
    public CompletableFuture<SendResult<String, String>> send(ScoreEvent event, String traceId) {
        String payload = serialise(event);
        String topic = topicResolver.scoreEvents();

        ProducerRecord<String, String> record = buildRecord(topic, event, payload, traceId);

        log.debug("Producing score event: matchId={}, eventId={}, topic={}, sequence={}",
                event.matchId(), event.eventId(), topic, event.eventSequence());

        Timer.Sample sample = Timer.start();

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(record).toCompletableFuture();

        future.whenComplete((result, ex) -> {
            sample.stop(sendLatencyTimer);
            if (ex != null) {
                callbackHandler.onFailure(event, ex);
            } else {
                callbackHandler.onSuccess(event, result);
            }
        });

        return future;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private String serialise(ScoreEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new ScoreEventSerializationException(
                    "Failed to serialise ScoreEvent with eventId=" + event.eventId(), e);
        }
    }

    private ProducerRecord<String, String> buildRecord(
            String topic, ScoreEvent event, String payload, String traceId) {
        ProducerRecord<String, String> record = new ProducerRecord<>(
                topic,
                null, // partition — let Kafka assign based on key hash
                event.matchId(), // key — partition routing for per-match ordering
                payload);

        // ── Observability headers ──────────────────────────────────────────────
        record.headers().add(new RecordHeader(HEADER_TRACE_ID,
                nullSafeBytes(traceId)));
        record.headers().add(new RecordHeader(HEADER_MATCH_ID,
                event.matchId().getBytes(StandardCharsets.UTF_8)));
        record.headers().add(new RecordHeader(HEADER_EVENT_ID,
                event.eventId().getBytes(StandardCharsets.UTF_8)));

        return record;
    }

    private byte[] nullSafeBytes(String value) {
        return value != null
                ? value.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
    }

    /**
     * Thrown when serialisation of a {@link ScoreEvent} fails before any network
     * call is made.
     * This is a programming error (e.g. a custom serialiser bug) and is never
     * retried.
     */
    public static class ScoreEventSerializationException extends RuntimeException {
        public ScoreEventSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
