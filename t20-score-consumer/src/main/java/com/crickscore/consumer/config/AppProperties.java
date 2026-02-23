package com.crickscore.consumer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Strongly-typed binding for {@code app.*} properties in
 * {@code application.yml}.
 *
 * <p>
 * Usage:
 * 
 * <pre>
 * &#64;Autowired
 * AppProperties props;
 * int concurrency = props.getKafka().getConsumer().getConcurrency();
 * String table = props.getDynamodb().getTable().getScoreEvents();
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Kafka kafka = new Kafka();
    private Dynamodb dynamodb = new Dynamodb();

    // ─── Kafka ────────────────────────────────────────────────────────────────

    @Data
    public static class Kafka {

        private Consumer consumer = new Consumer();
        private Topic topic = new Topic();

        @Data
        public static class Consumer {
            /**
             * Number of concurrent Kafka listener threads. Matches partition count / task
             * count.
             */
            private int concurrency = 8;
            /** Client ID prefix — a UUID suffix is appended at runtime. */
            private String clientId = "t20-score-consumer";
        }

        @Data
        public static class Topic {
            /** Main score events topic: {@code t20-match-scores} */
            private String scoreEvents = "t20-match-scores";
            /** Retry tier-1: {@code t20-match-scores-retry-1} */
            private String retry1 = "t20-match-scores-retry-1";
            /** Retry tier-2: {@code t20-match-scores-retry-2} */
            private String retry2 = "t20-match-scores-retry-2";
            /** Retry tier-3: {@code t20-match-scores-retry-3} */
            private String retry3 = "t20-match-scores-retry-3";
            /** Dead letter topic: {@code t20-match-scores-dlt} */
            private String dlt = "t20-match-scores-dlt";
        }
    }

    // ─── DynamoDB ─────────────────────────────────────────────────────────────

    @Data
    public static class Dynamodb {

        private Table table = new Table();

        @Data
        public static class Table {
            /** Event store table — PK: matchId, SK: eventSequence */
            private String scoreEvents = "t20-score-events";
            /** Materialized live score view — PK: matchId */
            private String liveScores = "t20-live-scores";
            /** Replay job state tracking — PK: matchId */
            private String replayState = "t20-replay-state";
        }
    }
}
