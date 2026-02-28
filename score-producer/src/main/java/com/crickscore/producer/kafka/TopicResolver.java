package com.crickscore.producer.kafka;

import com.crickscore.producer.config.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Resolves Kafka topic names from {@link AppProperties} at runtime.
 *
 * <p>
 * Centralising topic name resolution here avoids scattering
 * {@code appProperties.getKafka().getTopic().*()}
 * calls throughout services and keeps topic-name changes confined to
 * {@code application.yml}.
 */
@Component
public class TopicResolver {

    private final AppProperties appProperties;

    @Autowired
    public TopicResolver(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    /** @return the main score-events topic (e.g. {@code t20-match-scores}). */
    public String scoreEvents() {
        return appProperties.getKafka().getTopic().getScoreEvents();
    }

    /** @return retry tier-1 topic name. */
    public String retry1() {
        return appProperties.getKafka().getTopic().getRetry1();
    }

    /** @return retry tier-2 topic name. */
    public String retry2() {
        return appProperties.getKafka().getTopic().getRetry2();
    }

    /** @return retry tier-3 topic name. */
    public String retry3() {
        return appProperties.getKafka().getTopic().getRetry3();
    }

    /** @return dead-letter topic name. */
    public String dlt() {
        return appProperties.getKafka().getTopic().getDlt();
    }
}
