package com.crickscore.producer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link ScoreEventController}.
 *
 * <p>
 * Uses {@code @EmbeddedKafka} so the full Spring Boot context starts with a
 * real (in-process) Kafka broker. Each test publishes a score event via the
 * REST API and asserts that the correct record lands in the
 * {@code t20-match-scores} topic with the expected key and headers, verifying
 * the complete path:
 * 
 * <pre>
 *   HTTP POST → ScoreEventController → ScoreEventProducerService → Kafka
 * </pre>
 *
 * <h3>TASK-07.6 acceptance criteria</h3>
 * <ul>
 * <li>Embedded Kafka receives the event produced via REST.</li>
 * <li>Record key equals {@code matchId}.</li>
 * <li>{@code X-Trace-Id}, {@code X-Match-Id}, and {@code X-Event-Id} headers
 * are present.</li>
 * <li>Payload round-trips: JSON deserialises to the expected field values.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EmbeddedKafka(partitions = 1, topics = {
        "t20-match-scores",
        "t20-match-scores-retry-1",
        "t20-match-scores-retry-2",
        "t20-match-scores-retry-3",
        "t20-match-scores-dlt"
})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class ScoreEventControllerIntegrationTest {

    private static final String TOPIC = "t20-match-scores";
    private static final String MATCH_ID = "IPL-TEST-IT-001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private Consumer<String, String> consumer;

    @BeforeEach
    void createConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "it-consumer-group", "false", embeddedKafka);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumer = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        consumer.subscribe(Collections.singletonList(TOPIC));
    }

    @AfterEach
    void closeConsumer() {
        consumer.close();
    }

    // ── 202 Accepted ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/scores — valid payload")
    class ValidPayload {

        @Test
        @DisplayName("returns 202 Accepted with eventId and matchId in the body")
        void validPayload_returns202() throws Exception {
            String body = scoreEventJson(MATCH_ID, 1, 0, 1, "MI", 4, false, 4, 0);

            mockMvc.perform(post("/api/v1/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.eventId").isNotEmpty())
                    .andExpect(jsonPath("$.matchId").value(MATCH_ID))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("published record in Kafka has matchId as the record key")
        void validPayload_kafkaRecordKey_isMatchId() throws Exception {
            String uniqueMatchId = MATCH_ID + "-key-" + System.nanoTime();
            String body = scoreEventJson(uniqueMatchId, 1, 0, 1, "MI", 4, false, 4, 0);

            consumer.subscribe(Collections.singletonList(TOPIC));

            mockMvc.perform(post("/api/v1/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isAccepted());

            ConsumerRecord<String, String> record = pollOneRecord();
            assertThat(record.key()).isEqualTo(uniqueMatchId);
        }

        @Test
        @DisplayName("published record has X-Match-Id and X-Event-Id headers")
        void validPayload_kafkaHeaders_present() throws Exception {
            String uniqueMatchId = MATCH_ID + "-hdr-" + System.nanoTime();
            String body = scoreEventJson(uniqueMatchId, 1, 0, 2, "CSK", 6, false, 10, 0);

            mockMvc.perform(post("/api/v1/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isAccepted());

            ConsumerRecord<String, String> record = pollOneRecord();

            // All three observability headers must be present
            List<String> headerNames = List.of(record.headers().toArray()).stream()
                    .map(h -> h.key())
                    .toList();

            assertThat(headerNames).contains("X-Match-Id", "X-Event-Id", "X-Trace-Id");
        }

        @Test
        @DisplayName("X-Match-Id header value equals the matchId field")
        void validPayload_xMatchIdHeader_equalsMatchId() throws Exception {
            String uniqueMatchId = MATCH_ID + "-xmid-" + System.nanoTime();
            String body = scoreEventJson(uniqueMatchId, 1, 1, 3, "MI", 0, false, 11, 1);

            mockMvc.perform(post("/api/v1/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isAccepted());

            ConsumerRecord<String, String> record = pollOneRecord();

            byte[] xMatchId = record.headers().lastHeader("X-Match-Id").value();
            assertThat(new String(xMatchId)).isEqualTo(uniqueMatchId);
        }

        @Test
        @DisplayName("payload round-trip: Kafka record JSON deserialises to correct field values")
        void validPayload_payloadRoundTrip() throws Exception {
            String uniqueMatchId = MATCH_ID + "-rt-" + System.nanoTime();
            String body = scoreEventJson(uniqueMatchId, 2, 19, 6, "CSK", 1, true, 185, 6);

            MvcResult result = mockMvc.perform(post("/api/v1/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isAccepted())
                    .andReturn();

            // Extract eventId from response so we can assert it in the Kafka payload
            String responseJson = result.getResponse().getContentAsString();
            String returnedEventId = objectMapper.readTree(responseJson).get("eventId").asText();

            ConsumerRecord<String, String> record = pollOneRecord();
            var node = objectMapper.readTree(record.value());

            assertThat(node.get("matchId").asText()).isEqualTo(uniqueMatchId);
            assertThat(node.get("eventId").asText()).isEqualTo(returnedEventId);
            assertThat(node.get("inning").asInt()).isEqualTo(2);
            assertThat(node.get("over").asInt()).isEqualTo(19);
            assertThat(node.get("ball").asInt()).isEqualTo(6);
            assertThat(node.get("team").asText()).isEqualTo("CSK");
            assertThat(node.get("runs").asInt()).isEqualTo(1);
            assertThat(node.get("wicket").asBoolean()).isTrue();
            assertThat(node.get("totalRuns").asInt()).isEqualTo(185);
            assertThat(node.get("wickets").asInt()).isEqualTo(6);
        }

        @Test
        @DisplayName("null extras are not included in the Kafka payload (NON_NULL serialization)")
        void validPayload_nullExtras_omittedFromKafkaPayload() throws Exception {
            String uniqueMatchId = MATCH_ID + "-ext-" + System.nanoTime();
            String body = scoreEventJson(uniqueMatchId, 1, 0, 1, "MI", 4, false, 4, 0);

            mockMvc.perform(post("/api/v1/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isAccepted());

            ConsumerRecord<String, String> record = pollOneRecord();
            var node = objectMapper.readTree(record.value());

            assertThat(node.has("extras")).isFalse();
        }
    }

    // ── 400 Bad Request ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/scores — invalid payload (400)")
    class InvalidPayload {

        @Test
        @DisplayName("missing matchId returns 400 with field error")
        void missingMatchId_returns400() throws Exception {
            String body = """
                    {
                      "inning": 1, "over": 0, "ball": 1, "team": "MI",
                      "runs": 4, "wicket": false, "totalRuns": 4, "wickets": 0
                    }
                    """;

            mockMvc.perform(post("/api/v1/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors[0].field").value("matchId"));
        }

        @Test
        @DisplayName("inning out of range (3) returns 400")
        void inningOutOfRange_returns400() throws Exception {
            String body = scoreEventJson(MATCH_ID, 3, 0, 1, "MI", 4, false, 4, 0);

            mockMvc.perform(post("/api/v1/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("inning"));
        }

        @Test
        @DisplayName("over out of range (20) returns 400")
        void overOutOfRange_returns400() throws Exception {
            String body = scoreEventJson(MATCH_ID, 1, 20, 1, "MI", 4, false, 4, 0);

            mockMvc.perform(post("/api/v1/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("over"));
        }

        @Test
        @DisplayName("runs out of range (7) returns 400")
        void runsOutOfRange_returns400() throws Exception {
            String body = scoreEventJson(MATCH_ID, 1, 0, 1, "MI", 7, false, 7, 0);

            mockMvc.perform(post("/api/v1/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("runs"));
        }

        @Test
        @DisplayName("multiple invalid fields return 400 with multiple errors")
        void multipleInvalidFields_returns400WithMultipleErrors() throws Exception {
            String body = """
                    {
                      "inning": 3, "over": 25, "ball": 0
                    }
                    """;

            mockMvc.perform(post("/api/v1/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray())
                    .andExpect(jsonPath("$.errors.length()").value(
                            org.hamcrest.Matchers.greaterThan(1)));
        }

        @Test
        @DisplayName("empty body returns 400")
        void emptyBody_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Polls for a single record from the {@code t20-match-scores} topic,
     * waiting up to 10 seconds. Fails the test if no record arrives.
     */
    private ConsumerRecord<String, String> pollOneRecord() {
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        assertThat(records.count()).as("Expected exactly one record in Kafka").isGreaterThan(0);
        return records.iterator().next();
    }

    /** Builds a valid score event JSON string for the given parameters. */
    private String scoreEventJson(String matchId, int inning, int over, int ball,
            String team, int runs, boolean wicket,
            int totalRuns, int wickets) {
        return """
                {
                  "matchId":   "%s",
                  "inning":    %d,
                  "over":      %d,
                  "ball":      %d,
                  "team":      "%s",
                  "runs":      %d,
                  "wicket":    %b,
                  "totalRuns": %d,
                  "wickets":   %d
                }
                """.formatted(matchId, inning, over, ball, team, runs, wicket, totalRuns, wickets);
    }
}
