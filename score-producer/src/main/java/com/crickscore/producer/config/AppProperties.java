package com.crickscore.producer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Strongly-typed binding for {@code app.*} properties in application.yml.
 *
 * <p>Usage:
 * <pre>
 *   &#64;Autowired AppProperties props;
 *   String topic = props.getKafka().getTopic().getScoreEvents();
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Kafka kafka = new Kafka();

    @Data
    public static class Kafka {

        private Topic topic = new Topic();
        private Producer producer = new Producer();

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

        @Data
        public static class Producer {
            private String clientId = "t20-score-producer";
        }
    }
}
