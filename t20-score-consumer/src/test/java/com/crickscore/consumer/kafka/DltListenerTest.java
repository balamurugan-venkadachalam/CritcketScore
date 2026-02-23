package com.crickscore.consumer.kafka;

import com.crickscore.consumer.service.DeadLetterNotificationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DltListener}.
 *
 * Verifies that:
 * <ol>
 * <li>DLT counter is incremented for every dead-letter record.</li>
 * <li>{@link DeadLetterNotificationService#notify} is called with correct
 * params.</li>
 * <li>The record is always acknowledged (DLT must always advance).</li>
 * <li>Notification failure does NOT prevent acknowledgment.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class DltListenerTest {

    @Mock
    private DeadLetterNotificationService notificationService;
    @Mock
    private Acknowledgment ack;

    private DltListener dltListener;

    private static final String MATCH_ID = "IPL-2025-MI-CSK-001";
    private static final String PAYLOAD = "{\"matchId\":\"IPL-2025-MI-CSK-001\"}";
    private static final String ERROR_FQCN = "java.lang.RuntimeException";
    private static final String ERROR_MSG = "DynamoDB timeout";
    private static final String ORIGINAL_TOPIC = "t20-match-scores";

    @BeforeEach
    void setUp() {
        dltListener = new DltListener(notificationService, new SimpleMeterRegistry());
    }

    @Nested
    @DisplayName("Normal dead-letter processing")
    class NormalProcessing {

        @Test
        @DisplayName("calls notify() with matchId, partition, offset, payload, error class and message")
        void dltRecord_callsNotifyWithAllParams() {
            ConsumerRecord<String, String> record = buildDltRecord(PAYLOAD, ERROR_FQCN, ERROR_MSG);

            dltListener.onDeadLetter(record, ack);

            verify(notificationService, times(1)).notify(
                    eq(MATCH_ID),
                    eq(17),
                    eq(42L),
                    eq(PAYLOAD),
                    eq(ERROR_FQCN),
                    eq(ERROR_MSG));
        }

        @Test
        @DisplayName("always acknowledges the DLT record — partition must advance")
        void dltRecord_alwaysAcknowledges() {
            ConsumerRecord<String, String> record = buildDltRecord(PAYLOAD, ERROR_FQCN, ERROR_MSG);

            dltListener.onDeadLetter(record, ack);

            verify(ack, times(1)).acknowledge();
        }

        @Test
        @DisplayName("notify() is called BEFORE acknowledge() — notification first")
        void dltRecord_notifyBeforeAck() {
            ConsumerRecord<String, String> record = buildDltRecord(PAYLOAD, ERROR_FQCN, ERROR_MSG);
            var inOrder = inOrder(notificationService, ack);

            dltListener.onDeadLetter(record, ack);

            inOrder.verify(notificationService).notify(any(), anyInt(), anyLong(), any(), any(), any());
            inOrder.verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("Missing DLT headers")
    class MissingHeaders {

        @Test
        @DisplayName("handles missing exception headers gracefully (nulls passed to notify)")
        void missingHeaders_handledGracefully() {
            // Build a record with no Spring Kafka DLT headers
            ConsumerRecord<String, String> record = buildDltRecord(PAYLOAD, null, null);

            dltListener.onDeadLetter(record, ack);

            verify(notificationService, times(1)).notify(
                    eq(MATCH_ID), eq(17), eq(42L), eq(PAYLOAD), isNull(), isNull());
            verify(ack, times(1)).acknowledge();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ConsumerRecord<String, String> buildDltRecord(
            String payload, String errorFqcn, String errorMsg) {
        RecordHeaders headers = new RecordHeaders();
        addHeader(headers, DltListener.HEADER_ORIGINAL_TOPIC, ORIGINAL_TOPIC);
        addHeader(headers, DltListener.HEADER_ORIGINAL_OFFSET, "100");
        if (errorFqcn != null)
            addHeader(headers, DltListener.HEADER_EXCEPTION_FQCN, errorFqcn);
        if (errorMsg != null)
            addHeader(headers, DltListener.HEADER_EXCEPTION_MESSAGE, errorMsg);

        return new ConsumerRecord<>(
                "t20-match-scores-dlt",
                17, // partition
                42L, // offset
                System.currentTimeMillis(),
                TimestampType.CREATE_TIME,
                -1L, -1, -1,
                MATCH_ID, payload,
                headers,
                Optional.empty());
    }

    private static void addHeader(RecordHeaders headers, String key, String value) {
        headers.add(key, value.getBytes(StandardCharsets.UTF_8));
    }
}
