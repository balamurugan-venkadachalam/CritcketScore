package com.crickscore.consumer.repository;

import com.crickscore.consumer.config.AppProperties;
import org.springframework.stereotype.Component;

/**
 * Thin delegate that exposes table names from {@link AppProperties} to the
 * repository layer without coupling repositories directly to the full
 * {@link AppProperties} graph.
 *
 * <p>
 * Repositories inject this delegate rather than {@link AppProperties} so
 * that unit tests can supply table names directly without constructing a full
 * Spring context.
 */
@Component
public class AppPropertiesDelegate {

    private final AppProperties appProperties;

    public AppPropertiesDelegate(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String scoreEventsTable() {
        return appProperties.getDynamodb().getTable().getScoreEvents();
    }

    public String liveScoresTable() {
        return appProperties.getDynamodb().getTable().getLiveScores();
    }
}
