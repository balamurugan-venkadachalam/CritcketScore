package com.crickscore.consumer.kafka;

import com.crickscore.consumer.model.ScoreEvent;
import com.crickscore.consumer.service.ScoreEventService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Kafka listener for the {@code t20-match-scores} topic.
 *
 * <h3>Concurrency model</h3>
 * {@link KafkaConsumerConfig} sets {@code concurrency=8}; each thread owns a
 * dedicated partition. Because all balls of a match hash to the same partition,
 * one thread processes them in strict order.
 *
 * <h3>Retry strategy (TASK-12)</h3>
 * {@code @RetryableTopic} creates three retry topics with exponential backoff:
 * <ol>
 * <li>{@code t20-match-scores-retry-0} — retried after <b>1 s</b></li>
 * <li>{@code t20-match-scores-retry-1} — retried after <b>5 s</b></li>
 * <li>{@code t20-match-scores-retry-2} — retried after <b>15 s</b> (maxDelay
 * cap)</li>
 * </ol>
 * After exhausting all retries the record is forwarded to
 * {@code t20-match-scores-dlt}, handled by {@link DltListener}.
 * The original {@code matchId} key is preserved across all retry and DLT hops.
 *
 * <h3>Exception classification</h3>
 * <ul>
 * <li><b>Retryable</b> (default): {@link RuntimeException} — transient
 * DynamoDB/network failures that may succeed on retry.</li>
 * <li><b>Non-retryable / direct-to-DLT</b>: {@link JsonProcessingException}
 * (bad JSON — retrying won't help) and {@link ConstraintViolationException}
 * (validation failure — data problem). These bypass retry topics entirely.</li>
 * </ul>
 *
 * <h3>Poison-pill handling</h3>
 * {@link JsonProcessingException} is caught <em>inside</em> the listener method
 * and the record is acknowledged immediately so the partition advances. The DLT
 * exclusion above is a belt-and-braces safeguard in case the exception escapes.
 *
 * <h3>Acknowledgement</h3>
 * {@code AckMode.RECORD}: {@link Acknowledgment#acknowledge()} is called only
 * after {@link ScoreEventService#process} returns without error. Any uncaught
 * exception withholds the ACK; Spring Kafka then routes the record to the next
 * retry topic.
 */
@Slf4j
@Component
public class ScoreEventListener {

    static final String HEADER_TRACE_ID = "X-Trace-Id";
    static final String HEADER_EVENT_ID = "X-Event-Id";
    static final String MDC_TRACE_ID = "traceId";
    static final String MDC_MATCH_ID = "matchId";
    static final String MDC_EVENT_ID = "eventId";

    private final ScoreEventService scoreEventService;
    private final ObjectMapper objectMapper;
    private final Counter receivedCounter;
    private final Counter poisonPillCounter;

    @Autowired
    public ScoreEventListener(
            ScoreEventService scoreEventService,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        this.scoreEventService = scoreEventService;
        this.objectMapper = objectMapper;

        this.receivedCounter = Counter.builder("score.events.received.total")
                .description("Total score events received from Kafka")
                .register(meterRegistry);

        this.poisonPillCounter = Counter.builder("score.events.poison.total")
                .description("Score events skipped due to JSON parse failure (poison pills)")
                .register(meterRegistry);
    }

    /**
     * Handles a single score event from the {@code t20-match-scores} topic.
     *
     * @param record the raw Kafka record (payload + headers).
     * @param ack    manual acknowledgment — committed only after success.
     */
    @RetryableTopic(attempts = "4", // 1 original + 3 retries
            backoff = @Backoff(delay = 1_000, multiplier = 5, maxDelay = 15_000), dltTopicSuffix = "-dlt", topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE, dltStrategy = DltStrategy.FAIL_ON_ERROR,
            // notRetryOn: non-retryable errors go directly to DLT;
            // all other RuntimeExceptions are retried by default.
            exclude = { JsonProcessingException.class, ConstraintViolationException.class })
    @KafkaListener(topics = "${app.kafka.topic.score-events}", groupId = "t20-score-consumer-group", containerFactory = "kafkaListenerContainerFactory")
    public void onScoreEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = extractHeader(record, HEADER_TRACE_ID);
        String eventId = extractHeader(record, HEADER_EVENT_ID);
        String matchId = record.key();

        MDC.put(MDC_TRACE_ID, traceId != null ? traceId : "");
        MDC.put(MDC_MATCH_ID, matchId != null ? matchId : "");
        MDC.put(MDC_EVENT_ID, eventId != null ? eventId : "");

        try {
            receivedCounter.increment();

            log.debug("Received score event: topic={}, partition={}, offset={}, matchId={}",
                    record.topic(), record.partition(), record.offset(), matchId);

            // ── Deserialise ───────────────────────────────────────────────────
            ScoreEvent event;
            try {
                event = objectMapper.readValue(record.value(), ScoreEvent.class);
            } catch (JsonProcessingException e) {
                // Poison pill — bad JSON, retrying won't help. ACK to unblock the partition.
                poisonPillCounter.increment();
                log.error("Poison pill: cannot deserialise score event. " +
                        "topic={}, partition={}, offset={}, error={}",
                        record.topic(), record.partition(), record.offset(), e.getMessage());
                ack.acknowledge();
                return;
            }

            // ── Process ───────────────────────────────────────────────────────
            // Any RuntimeException propagating here will be caught by @RetryableTopic
            // and routed to a retry topic. The ACK below is only reached on success.
            scoreEventService.process(event, traceId);

            ack.acknowledge();

        } finally {
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_MATCH_ID);
            MDC.remove(MDC_EVENT_ID);
        }
    }

    private String extractHeader(ConsumerRecord<?, ?> record, String headerName) {
        var header = record.headers().lastHeader(headerName);
        if (header == null || header.value() == null)
            return null;
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
