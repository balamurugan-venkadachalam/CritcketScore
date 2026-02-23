package com.crickscore.producer.kafka;

import com.crickscore.producer.config.AppProperties;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer factory and template configuration.
 *
 * <p>
 * Key reliability settings:
 * <ul>
 * <li>{@code enable.idempotence=true} — exactly-once delivery per partition
 * sequence.</li>
 * <li>{@code acks=all} — all in-sync replicas must acknowledge before
 * success.</li>
 * <li>{@code retries=Integer.MAX_VALUE} — retry indefinitely;
 * {@code delivery.timeout.ms} governs the wall-clock bound.</li>
 * <li>{@code max.in.flight.requests.per.connection=5} — max concurrent
 * unacknowledged requests (safe with idempotence enabled since Kafka 1.1).</li>
 * <li>{@code compression.type=zstd} — best compression ratio for JSON payloads,
 * CPU-efficient on modern JVMs.</li>
 * <li>{@code linger.ms=5} — small batching window to improve throughput without
 * materially impacting P99 latency.</li>
 * <li>{@code batch.size=65536} — 64 KB batch ceiling per partition-leader
 * connection.</li>
 * </ul>
 *
 * <p>
 * <b>Note:</b> TLS/SASL properties for production are injected via
 * {@code application-prod.yml}
 * and environment variables; this config only sets the producer reliability
 * knobs that are
 * profile-agnostic.
 */
@Configuration
public class KafkaProducerConfig {

    private final AppProperties appProperties;

    @Autowired
    public KafkaProducerConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /**
     * Additional producer properties applied on top of what Spring Boot
     * auto-configures
     * from {@code application.yml} / {@code application-{profile}.yml}.
     *
     * <p>
     * Spring Boot's {@code KafkaAutoConfiguration} merges these properties with
     * those
     * already parsed from YAML, so profile-specific settings (bootstrap servers,
     * TLS) are
     * not overridden here.
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // ── Serialisers ───────────────────────────────────────────────────────
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // ── Reliability ───────────────────────────────────────────────────────
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // ── Throughput / batching ─────────────────────────────────────────────
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 65_536);

        // ── Timeouts ──────────────────────────────────────────────────────────
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000); // 2 min wall-clock
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000); // per-request

        // ── Client identity (aids Kafka broker-side logging) ──────────────────
        configProps.put(ProducerConfig.CLIENT_ID_CONFIG,
                appProperties.getKafka().getProducer().getClientId());

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * {@link KafkaTemplate} backed by the idempotent {@link ProducerFactory}.
     *
     * <p>
     * This is the primary send mechanism for
     * {@link com.crickscore.producer.kafka.ScoreEventProducerService}.
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
