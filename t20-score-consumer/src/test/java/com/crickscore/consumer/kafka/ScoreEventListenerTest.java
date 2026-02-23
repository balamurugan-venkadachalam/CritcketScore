package com.crickscore.consumer.kafka;

import com.crickscore.consumer.model.ScoreEvent;
import com.crickscore.consumer.service.ScoreEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
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
import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ScoreEventListener}.
 *
 * Tests are pure unit tests — no Spring context, no Kafka broker.
 * The listener is constructed directly with mocked collaborators.
 *
 * <p>
 * Scenarios covered:
 * <ol>
 * <li>Valid JSON → {@link ScoreEventService#process} called once, ACK
 * sent.</li>
 * <li>Invalid JSON (poison pill) → ACK sent immediately, {@code process} NOT
 * called.</li>
 * <li>{@code process()} throws a runtime exception → ACK NOT sent (consumer
 * retries).</li>
 * <li>Missing headers → listener handles nulls gracefully, still calls
 * {@code process}.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ScoreEventListenerTest {

    @Mock
    private ScoreEventService scoreEventService;
    @Mock
    private Acknowledgment ack;

    private ScoreEventListener listener;
    private ObjectMapper objectMapper;

    private static final String MATCH_ID = "IPL-2025-MI-CSK-001";
    private static final String EVENT_ID = "test-event-001";
    private static final String TRACE_ID = "trace-abc-123";
    private static final Instant NOW = Instant.parse("2025-04-15T14:32:01Z");

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new ParameterNamesModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        listener = new ScoreEventListener(scoreEventService, objectMapper, new SimpleMeterRegistry());
    }

    // ─── Valid message ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Valid JSON payload")
    class ValidPayload {

        @Test
        @DisplayName("calls process() once and acknowledges the message")
        void validPayload_callsServiceAndAcks() throws Exception {
            ScoreEvent event = buildEvent();
            String payload = objectMapper.writeValueAsString(event);
            ConsumerRecord<String, String> record = buildRecord(payload, TRACE_ID, EVENT_ID);

            listener.onScoreEvent(record, ack);

            verify(scoreEventService, times(1)).process(any(ScoreEvent.class), eq(TRACE_ID));
            verify(ack, times(1)).acknowledge();
        }

        @Test
        @DisplayName("ACK is only called AFTER process() returns, not before")
        void validPayload_ackAfterProcess() throws Exception {
            ScoreEvent event = buildEvent();
            String payload = objectMapper.writeValueAsString(event);
            ConsumerRecord<String, String> record = buildRecord(payload, TRACE_ID, EVENT_ID);

            var inOrder = inOrder(scoreEventService, ack);

            listener.onScoreEvent(record, ack);

            inOrder.verify(scoreEventService).process(any(), anyString());
            inOrder.verify(ack).acknowledge();
        }
    }

    // ─── Poison pill (bad JSON) ────────────────────────────────────────────────

    @Nested
    @DisplayName("Invalid JSON payload (poison pill)")
    class PoisonPill {

        @Test
        @DisplayName("acknowledges immediately without calling process()")
        void invalidJson_acksWithoutCallingProcess() {
            ConsumerRecord<String, String> record = buildRecord("{not-valid-json}", TRACE_ID, EVENT_ID);

            listener.onScoreEvent(record, ack);

            verify(scoreEventService, never()).process(any(), any());
            verify(ack, times(1)).acknowledge();
        }

        @Test
        @DisplayName("empty string payload is treated as a poison pill")
        void emptyPayload_treatedAsPoisonPill() {
            // Empty string is not valid JSON — realistic Kafka scenario
            ConsumerRecord<String, String> record = buildRecord("", TRACE_ID, EVENT_ID);

            listener.onScoreEvent(record, ack);

            verify(scoreEventService, never()).process(any(), any());
            verify(ack, times(1)).acknowledge();
        }
    }

    // ─── Processing failure ────────────────────────────────────────────────────

    @Nested
    @DisplayName("ScoreEventService throws")
    class ProcessingFailure {

        @Test
        @DisplayName("does NOT acknowledge when process() throws a RuntimeException")
        void processThrows_doesNotAck() throws Exception {
            ScoreEvent event = buildEvent();
            String payload = objectMapper.writeValueAsString(event);
            ConsumerRecord<String, String> record = buildRecord(payload, TRACE_ID, EVENT_ID);

            doThrow(new RuntimeException("DynamoDB timeout"))
                    .when(scoreEventService).process(any(), any());

            try {
                listener.onScoreEvent(record, ack);
            } catch (RuntimeException ignored) {
                // Exception is expected to propagate so container can retry
            }

            verify(ack, never()).acknowledge();
        }
    }

    // ─── Missing headers ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Missing Kafka headers")
    class MissingHeaders {

        @Test
        @DisplayName("handles missing X-Trace-Id header gracefully")
        void missingTraceId_doesNotFail() throws Exception {
            ScoreEvent event = buildEvent();
            String payload = objectMapper.writeValueAsString(event);
            // Build record with no headers
            ConsumerRecord<String, String> record = buildRecord(payload, null, EVENT_ID);

            listener.onScoreEvent(record, ack);

            verify(scoreEventService, times(1)).process(any(ScoreEvent.class), isNull());
            verify(ack, times(1)).acknowledge();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScoreEvent buildEvent() {
        return ScoreEvent.builder()
                .eventId(EVENT_ID)
                .matchId(MATCH_ID)
                .inning(1).over(3).ball(2)
                .team("MI").runs(4).extras(0)
                .wicket(false).totalRuns(67).wickets(2)
                .timestamp(NOW)
                .build();
    }

    private ConsumerRecord<String, String> buildRecord(
            String payload, String traceId, String eventId) {
        RecordHeaders headers = new RecordHeaders();
        if (traceId != null) {
            headers.add(ScoreEventListener.HEADER_TRACE_ID,
                    traceId.getBytes(StandardCharsets.UTF_8));
        }
        if (eventId != null) {
            headers.add(ScoreEventListener.HEADER_EVENT_ID,
                    eventId.getBytes(StandardCharsets.UTF_8));
        }
        return new ConsumerRecord<>(
                "t20-match-scores",
                17, // partition
                100L, // offset
                System.currentTimeMillis(),
                TimestampType.CREATE_TIME,
                -1L, -1, -1,
                MATCH_ID, // key
                payload, // value
                headers,
                Optional.empty());
    }
}
