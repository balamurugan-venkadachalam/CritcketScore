package com.crickscore.consumer.kafka;

import com.crickscore.consumer.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka consumer factory and listener container factory configuration.
 *
 * <h3>Key settings</h3>
 * <ul>
 * <li>{@code AckMode.RECORD} — offset is committed only after the listener
 * method
 * returns successfully (post-DynamoDB write). No message is silently
 * dropped.</li>
 * <li>{@code concurrency=8} — 8 listener threads per pod × 24 ECS pods = 192
 * threads,
 * matching the 192-partition topic exactly (1 thread per partition).</li>
 * <li>{@code CooperativeStickyAssignor} — incremental rebalancing: only
 * reassigned
 * partitions pause during a rolling deploy; other partitions keep
 * consuming.</li>
 * <li>{@code group.instance.id} — static membership: a pod restarting within
 * {@code session.timeout.ms} (45s) rejoins without triggering a rebalance.</li>
 * <li>{@code isolation.level=read_committed} — only reads messages from
 * committed
 * producer transactions (pairs with producer
 * {@code enable.idempotence=true}).</li>
 * <li>{@code DefaultErrorHandler(FixedBackOff(0, 0))} — zero retries inside the
 * container. All retry/DLT logic is handled by {@code @RetryableTopic}
 * (TASK-12),
 * which routes failures to separate retry topics rather than blocking the
 * partition.</li>
 * </ul>
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

        private final AppProperties appProperties;

        /**
         * Resolved from {@code spring.kafka.bootstrap-servers} — profile-specific
         * value.
         */
        @Value("${spring.kafka.bootstrap-servers}")
        private String bootstrapServers;

        @Autowired
        public KafkaConsumerConfig(AppProperties appProperties) {
                this.appProperties = appProperties;
        }

        /**
         * {@link ConsumerFactory} producing {@link StringDeserializer}-based consumers.
         *
         * <p>
         * Bootstrap servers, security protocol, and other profile-specific
         * properties are merged from {@code application-{profile}.yml} by Spring Boot's
         * auto-configuration. Only the settings that cannot be expressed in YAML
         * (or that require programmatic construction) are set here.
         */
        @Bean
        public ConsumerFactory<String, String> consumerFactory() {
                Map<String, Object> props = new HashMap<>();

                // ── Bootstrap servers (injected from spring.kafka.bootstrap-servers) ────
                props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

                // ── Deserialisers ─────────────────────────────────────────────────────
                props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

                // ── Group & offset ────────────────────────────────────────────────────
                props.put(ConsumerConfig.GROUP_ID_CONFIG, "t20-score-consumer-group");
                props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
                props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // AckMode.RECORD controls commits

                // ── Throughput / fetch ────────────────────────────────────────────────
                props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
                props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
                props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

                // ── Exactly-once read pairing with producer idempotence ───────────────
                props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");

                // ── Rebalancing strategy ──────────────────────────────────────────────
                // CooperativeStickyAssignor: only reassigned partitions pause during rebalance.
                // RangeAssignor and RoundRobinAssignor (defaults) stop ALL partitions.
                props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
                                List.of(CooperativeStickyAssignor.class));

                // ── Static membership ─────────────────────────────────────────────────
                // A unique, stable ID per pod. Pod restarts within session.timeout.ms (45s)
                // rejoin the group without triggering a full rebalance.
                String instanceId = System.getenv().getOrDefault("HOSTNAME", "consumer")
                                + "-" + appProperties.getKafka().getConsumer().getClientId();
                props.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, instanceId);

                // ── Session / heartbeat timeouts ──────────────────────────────────────
                props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45_000);
                props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 15_000);

                // ── Client identity (aids Kafka broker logs) ──────────────────────────
                props.put(ConsumerConfig.CLIENT_ID_CONFIG,
                                appProperties.getKafka().getConsumer().getClientId());

                log.info("Kafka consumer factory configured: groupId=t20-score-consumer-group, " +
                                "concurrency={}, isolation=read_committed, assignor=CooperativeSticky",
                                appProperties.getKafka().getConsumer().getConcurrency());

                return new DefaultKafkaConsumerFactory<>(props);
        }

        /**
         * {@link ConcurrentKafkaListenerContainerFactory} used by all
         * {@code @KafkaListener} annotated methods in this application.
         *
         * <p>
         * {@link AckMode#RECORD} means the offset is committed after each record's
         * listener method returns without throwing. If the listener throws, the offset
         * is NOT committed and the {@link DefaultErrorHandler} handles the failure.
         *
         * <p>
         * Error handling:
         * <ul>
         * <li>{@link FixedBackOff} with {@code (interval=0, maxAttempts=0)} means
         * zero local retries — the record goes to the error handler immediately.</li>
         * <li>The {@link DefaultErrorHandler} will call
         * {@code ConsumerAwareBatchErrorHandler} logic, which (with no retryable
         * topics configured yet) will publish the failed record to the DLT.
         * {@code @RetryableTopic} in TASK-12 will intercept before DLT and route
         * through retry topics first.</li>
         * </ul>
         */
        @Bean
        public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
                        ConsumerFactory<String, String> consumerFactory) {

                ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();

                factory.setConsumerFactory(consumerFactory);

                // ── Concurrency: 1 thread per partition ───────────────────────────────
                factory.setConcurrency(appProperties.getKafka().getConsumer().getConcurrency());

                // ── Manual offset commit: only after successful DynamoDB write ─────────
                factory.getContainerProperties().setAckMode(AckMode.RECORD);

                // ── Error handler: no local retries; let @RetryableTopic (TASK-12) handle ─
                factory.setCommonErrorHandler(
                                new DefaultErrorHandler(new FixedBackOff(0L, 0L)));

                return factory;
        }
}
