package com.crickscore.consumer.kafka;

import com.crickscore.consumer.service.DeadLetterNotificationService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Kafka listener for the {@code t20-match-scores-dlt} dead-letter topic.
 *
 * <p>
 * A record lands here after exhausting all retry attempts in the
 * {@code t20-match-scores-retry-*} chain configured by
 * {@code @RetryableTopic} in {@link ScoreEventListener}.
 *
 * <h3>What this listener does</h3>
 * <ol>
 * <li>Logs the failed record with full context (topic, partition, offset,
 * matchId, error cause from headers).</li>
 * <li>Increments the {@code score.events.dlt.total} Micrometer counter for
 * alerting and dashboards.</li>
 * <li>Calls {@link DeadLetterNotificationService#notify} to dispatch an SNS
 * alert to the ops team.</li>
 * <li>Acknowledges the record so the DLT partition advances.</li>
 * </ol>
 *
 * <h3>Partition guarantee</h3>
 * Spring Kafka's {@code @RetryableTopic} forwards the original record key
 * ({@code matchId}) to the DLT, so the DLT record lands on the same partition
 * index as the original. The DLT listener is configured with
 * {@code concurrency=8}
 * (via the shared {@code kafkaListenerContainerFactory}), matching the main
 * topic.
 */
@Slf4j
@Component
public class DltListener {

    static final String HEADER_EXCEPTION_MESSAGE = "kafka_dlt-exception-message";
    static final String HEADER_EXCEPTION_FQCN = "kafka_dlt-exception-fqcn";
    static final String HEADER_ORIGINAL_TOPIC = "kafka_dlt-original-topic";
    static final String HEADER_ORIGINAL_OFFSET = "kafka_dlt-original-offset";

    private final DeadLetterNotificationService notificationService;
    private final Counter dltCounter;

    @Autowired
    public DltListener(
            DeadLetterNotificationService notificationService,
            MeterRegistry meterRegistry) {
        this.notificationService = notificationService;
        this.dltCounter = Counter.builder("score.events.dlt.total")
                .description("Total score events routed to the dead-letter topic after exhausting retries")
                .register(meterRegistry);
    }

    /**
     * Processes a dead-lettered score event.
     *
     * @param record the failed record forwarded to the DLT by Spring Kafka.
     * @param ack    manual acknowledgment — committed after notification is sent.
     */
    @KafkaListener(topics = "${app.kafka.topic.dlt}", groupId = "t20-score-consumer-group", containerFactory = "kafkaListenerContainerFactory")
    public void onDeadLetter(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String matchId = record.key();
        String exceptionMsg = extractHeader(record, HEADER_EXCEPTION_MESSAGE);
        String exceptionFqcn = extractHeader(record, HEADER_EXCEPTION_FQCN);
        String originalTopic = extractHeader(record, HEADER_ORIGINAL_TOPIC);
        String originalOffset = extractHeader(record, HEADER_ORIGINAL_OFFSET);

        MDC.put("matchId", matchId != null ? matchId : "");

        try {
            dltCounter.increment();

            log.error("Dead-letter event received: matchId={}, partition={}, offset={}, " +
                    "originalTopic={}, originalOffset={}, error=[{}] {}",
                    matchId, record.partition(), record.offset(),
                    originalTopic, originalOffset, exceptionFqcn, exceptionMsg);

            notificationService.notify(
                    matchId,
                    record.partition(),
                    record.offset(),
                    record.value(),
                    exceptionFqcn,
                    exceptionMsg);

            ack.acknowledge();

        } finally {
            MDC.remove("matchId");
        }
    }

    private String extractHeader(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        if (header == null || header.value() == null)
            return null;
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
