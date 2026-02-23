package com.crickscore.producer.kafka;

import com.crickscore.producer.model.ScoreEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static com.crickscore.producer.kafka.ScoreEventProducerService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ScoreEventProducerService}.
 *
 * <p>
 * Uses a {@link SimpleMeterRegistry} (no-op counters/timers) and mocked
 * {@link KafkaTemplate} — no real broker required.
 */
@ExtendWith(MockitoExtension.class)
class ScoreEventProducerServiceTest {

    private static final String TOPIC = "t20-match-scores";
    private static final String TRACE_ID = "abc123def456";
    private static final String MATCH_ID = "IPL-2025-MI-CSK-001";
    private static final String EVENT_ID = "evt-00000001";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ProducerCallbackHandler callbackHandler;

    @Mock
    private TopicResolver topicResolver;

    @Captor
    private ArgumentCaptor<ProducerRecord<String, String>> recordCaptor;

    private ScoreEventProducerService service;
    private MeterRegistry meterRegistry;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // NOTE: topicResolver.scoreEvents() is stubbed inside successfulSend().
        // Do NOT stub it here to avoid UnnecessaryStubbingException on tests
        // that never invoke send() (e.g. serialisation-failure and timer tests).
        service = new ScoreEventProducerService(
                kafkaTemplate, objectMapper, callbackHandler, topicResolver, meterRegistry);
    }

    // ─── Topic routing ────────────────────────────────────────────────────────

    @Test
    @DisplayName("send() routes record to the correct topic")
    void send_routesToCorrectTopic() throws Exception {
        ScoreEvent event = buildEvent();
        successfulSend();

        service.send(event, TRACE_ID);

        verify(kafkaTemplate).send(recordCaptor.capture());
        assertThat(recordCaptor.getValue().topic()).isEqualTo(TOPIC);
    }

    // ─── Partition key ────────────────────────────────────────────────────────

    @Test
    @DisplayName("send() uses matchId as the Kafka partition key")
    void send_usesMatchIdAsPartitionKey() throws Exception {
        ScoreEvent event = buildEvent();
        successfulSend();

        service.send(event, TRACE_ID);

        verify(kafkaTemplate).send(recordCaptor.capture());
        assertThat(recordCaptor.getValue().key()).isEqualTo(MATCH_ID);
    }

    // ─── Headers ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("send() sets X-Trace-Id header")
    void send_setsTraceIdHeader() throws Exception {
        ScoreEvent event = buildEvent();
        successfulSend();

        service.send(event, TRACE_ID);

        verify(kafkaTemplate).send(recordCaptor.capture());
        String header = headerValue(recordCaptor.getValue(), HEADER_TRACE_ID);
        assertThat(header).isEqualTo(TRACE_ID);
    }

    @Test
    @DisplayName("send() sets X-Match-Id header")
    void send_setsMatchIdHeader() throws Exception {
        ScoreEvent event = buildEvent();
        successfulSend();

        service.send(event, TRACE_ID);

        verify(kafkaTemplate).send(recordCaptor.capture());
        String header = headerValue(recordCaptor.getValue(), HEADER_MATCH_ID);
        assertThat(header).isEqualTo(MATCH_ID);
    }

    @Test
    @DisplayName("send() sets X-Event-Id header")
    void send_setsEventIdHeader() throws Exception {
        ScoreEvent event = buildEvent();
        successfulSend();

        service.send(event, TRACE_ID);

        verify(kafkaTemplate).send(recordCaptor.capture());
        String header = headerValue(recordCaptor.getValue(), HEADER_EVENT_ID);
        assertThat(header).isEqualTo(EVENT_ID);
    }

    @Test
    @DisplayName("send() sets X-Trace-Id to empty bytes when traceId is null")
    void send_handlesNullTraceId() throws Exception {
        ScoreEvent event = buildEvent();
        successfulSend();

        service.send(event, null);

        verify(kafkaTemplate).send(recordCaptor.capture());
        byte[] headerBytes = recordCaptor.getValue().headers().lastHeader(HEADER_TRACE_ID).value();
        assertThat(headerBytes).isEmpty();
    }

    // ─── Payload ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("send() payload deserialises back to the original ScoreEvent")
    void send_payloadRoundTrip() throws Exception {
        ScoreEvent event = buildEvent();
        successfulSend();

        service.send(event, TRACE_ID);

        verify(kafkaTemplate).send(recordCaptor.capture());
        String json = recordCaptor.getValue().value();
        ScoreEvent deserialized = objectMapper.readValue(json, ScoreEvent.class);
        assertThat(deserialized.eventId()).isEqualTo(event.eventId());
        assertThat(deserialized.matchId()).isEqualTo(event.matchId());
        assertThat(deserialized.runs()).isEqualTo(event.runs());
    }

    // ─── Callbacks ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("send() invokes onSuccess callback when broker ACKs")
    void send_invokesSuccessCallback() throws Exception {
        ScoreEvent event = buildEvent();
        successfulSend();

        service.send(event, TRACE_ID).get();

        verify(callbackHandler).onSuccess(eq(event), any(SendResult.class));
        verify(callbackHandler, never()).onFailure(any(), any());
    }

    @Test
    @DisplayName("send() invokes onFailure callback on broker error")
    void send_invokesFailureCallback() throws Exception {
        ScoreEvent event = buildEvent();
        RuntimeException brokerError = new RuntimeException("Broker unavailable");

        when(topicResolver.scoreEvents()).thenReturn(TOPIC);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(brokerError);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failed);

        service.send(event, TRACE_ID);

        // Give the whenComplete handler a moment to fire on the test thread
        Thread.sleep(50);

        verify(callbackHandler).onFailure(eq(event), eq(brokerError));
        verify(callbackHandler, never()).onSuccess(any(), any());
    }

    // ─── Serialisation error ──────────────────────────────────────────────────

    @Test
    @DisplayName("send() throws ScoreEventSerializationException when Jackson fails")
    void send_throwsOnSerialisationFailure() throws Exception {
        ObjectMapper broken = mock(ObjectMapper.class);
        doThrow(new JsonProcessingException("boom") {
        }).when(broken).writeValueAsString(any());

        // serialise() is called before topicResolver — topicResolver will NOT be
        // invoked.
        // Create a dedicated service with a lenient topicResolver to avoid unnecessary
        // stubbing.
        TopicResolver lenientResolver = mock(TopicResolver.class, withSettings().lenient());
        lenient().when(lenientResolver.scoreEvents()).thenReturn(TOPIC);

        ScoreEventProducerService brokenService = new ScoreEventProducerService(
                kafkaTemplate, broken, callbackHandler, lenientResolver, meterRegistry);

        ScoreEvent event = buildEvent();

        assertThatThrownBy(() -> brokenService.send(event, TRACE_ID))
                .isInstanceOf(ScoreEventProducerService.ScoreEventSerializationException.class)
                .hasMessageContaining("Failed to serialise");
    }

    // ─── Metrics ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("send.latency timer is registered in the meter registry after construction")
    void send_registersLatencyTimer() {
        // Timer is registered during ScoreEventProducerService construction — no send()
        // needed.
        // Use a fresh registry so the assertion is independent of other tests.
        MeterRegistry freshRegistry = new SimpleMeterRegistry();
        new ScoreEventProducerService(
                kafkaTemplate, objectMapper, callbackHandler, topicResolver, freshRegistry);
        assertThat(freshRegistry.find("score.event.send.latency").timer()).isNotNull();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ScoreEvent buildEvent() {
        return ScoreEvent.builder()
                .eventId(EVENT_ID)
                .matchId(MATCH_ID)
                .inning(1)
                .over(3)
                .ball(2)
                .team("MI")
                .runs(4)
                .extras(0)
                .wicket(false)
                .totalRuns(67)
                .wickets(2)
                .timestamp(Instant.parse("2025-04-15T14:32:01Z"))
                .build();
    }

    @SuppressWarnings("unchecked")
    private void successfulSend() {
        // Stub topicResolver here so only tests that call successfulSend() trigger it,
        // avoiding UnnecessaryStubbingException in tests that never reach Kafka.
        when(topicResolver.scoreEvents()).thenReturn(TOPIC);

        RecordMetadata meta = new RecordMetadata(
                new TopicPartition(TOPIC, 5), 42L, 0, 0L, 20, 500);
        SendResult<String, String> result = new SendResult<>(null, meta);

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(result);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);
    }

    private String headerValue(ProducerRecord<?, ?> record, String headerName) {
        return new String(record.headers().lastHeader(headerName).value(),
                java.nio.charset.StandardCharsets.UTF_8);
    }
}
