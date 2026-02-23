package com.crickscore.producer.kafka;

import com.crickscore.producer.config.AppProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic declarations for the T20 score-events pipeline.
 *
 * <p>
 * Topics are only auto-created when running in the {@code local} or
 * {@code test} profiles.
 * In production, topics are pre-provisioned by CDK ({@code lib/msk-stack.ts})
 * to ensure
 * the correct replication factor (3) and broker-side configs are applied
 * exactly once.
 *
 * <h3>Topic layout</h3>
 * 
 * <pre>
 *  t20-match-scores          ← main ingest topic (192 partitions, RF=3)
 *  t20-match-scores-retry-1  ← retry tier 1 (1 s initial delay)
 *  t20-match-scores-retry-2  ← retry tier 2 (5 s delay)
 *  t20-match-scores-retry-3  ← retry tier 3 (15 s delay)
 *  t20-match-scores-dlt      ← dead-letter topic (permanent storage, 7 days)
 * </pre>
 *
 * <h3>Partition count rationale</h3>
 * 192 partitions = 24 consumer tasks × 8 threads/task. This aligns the topic's
 * parallelism with the ECS consumer service's maximum concurrency, capping
 * consumer
 * lag growth under peak load.
 */
@Configuration
@Profile({ "local", "test" }) // Auto-create only for local / test; prod topics are CDK-managed
public class KafkaTopicConfig {

    /** 7 days in milliseconds — long enough for replays and investigations. */
    private static final String RETENTION_7_DAYS_MS = String.valueOf(7L * 24 * 60 * 60 * 1_000);

    /** 3 days retention for retry/DLT topics (sufficient for ops triage). */
    private static final String RETENTION_3_DAYS_MS = String.valueOf(3L * 24 * 60 * 60 * 1_000);

    /**
     * Minimum in-sync replicas.
     *
     * <p>
     * Set to 2 on the main topic so a produce with {@code acks=all} still succeeds
     * when
     * one of the three broker replicas is temporarily out-of-sync (planned
     * maintenance, restart).
     * Setting it to 1 for retry/DLT topics reduces write amplification for
     * low-volume flows.
     */
    private static final String MIN_ISR_2 = "2";
    private static final String MIN_ISR_1 = "1";

    private final AppProperties appProperties;

    @Autowired
    public KafkaTopicConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    // ─── Main Topic ───────────────────────────────────────────────────────────

    /**
     * Primary score-events topic.
     *
     * <ul>
     * <li>192 partitions — one per consumer thread at maximum scale.</li>
     * <li>Replication factor 1 for local (single-broker Docker). CDK sets RF=3 for
     * prod.</li>
     * <li>{@code min.insync.replicas=1} for local; CDK overrides to 2 in prod.</li>
     * <li>7-day retention to support the Replay API (TASK-13).</li>
     * </ul>
     */
    @Bean
    public NewTopic scoreEventsTopic() {
        return TopicBuilder.name(appProperties.getKafka().getTopic().getScoreEvents())
                .partitions(192)
                .replicas(1) // local: single broker; prod CDK uses 3
                .config(TopicConfig.RETENTION_MS_CONFIG, RETENTION_7_DAYS_MS)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, MIN_ISR_1)
                .config(TopicConfig.COMPRESSION_TYPE_CONFIG, "producer") // honour producer-level setting
                .build();
    }

    // ─── Retry Topics ─────────────────────────────────────────────────────────

    /**
     * Retry tier 1 — events re-attempted after ~1 s.
     * Partition count mirrors the main topic to preserve per-match ordering.
     */
    @Bean
    public NewTopic retryTopic1() {
        return TopicBuilder.name(appProperties.getKafka().getTopic().getRetry1())
                .partitions(192)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, RETENTION_3_DAYS_MS)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, MIN_ISR_1)
                .build();
    }

    /** Retry tier 2 — events re-attempted after ~5 s. */
    @Bean
    public NewTopic retryTopic2() {
        return TopicBuilder.name(appProperties.getKafka().getTopic().getRetry2())
                .partitions(192)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, RETENTION_3_DAYS_MS)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, MIN_ISR_1)
                .build();
    }

    /** Retry tier 3 — events re-attempted after ~15 s (final retry before DLT). */
    @Bean
    public NewTopic retryTopic3() {
        return TopicBuilder.name(appProperties.getKafka().getTopic().getRetry3())
                .partitions(192)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, RETENTION_3_DAYS_MS)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, MIN_ISR_1)
                .build();
    }

    // ─── Dead Letter Topic ────────────────────────────────────────────────────

    /**
     * Dead-letter topic — events that exhausted all retries or hit a non-retryable
     * exception.
     *
     * <p>
     * Partition count matches the main topic so {@code matchId} key routing places
     * a failed
     * event on the same partition number as its source event — simplifying
     * correlated queries.
     */
    @Bean
    public NewTopic dltTopic() {
        return TopicBuilder.name(appProperties.getKafka().getTopic().getDlt())
                .partitions(192)
                .replicas(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, RETENTION_3_DAYS_MS)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, MIN_ISR_1)
                .build();
    }
}
