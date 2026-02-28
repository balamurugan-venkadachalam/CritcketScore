package com.crickscore.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * T20 Live Score Consumer Application.
 *
 * <p>Consumes {@code ScoreEvent} messages from Kafka topic {@code t20-match-scores},
 * persists events to DynamoDB, and maintains live score materialized views.
 */
@SpringBootApplication
public class T20ScoreConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(T20ScoreConsumerApplication.class, args);
    }
}
