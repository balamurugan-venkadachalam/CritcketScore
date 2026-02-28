package com.crickscore.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * T20 Live Score Producer Application.
 *
 * <p>Produces {@code ScoreEvent} messages to the Kafka topic {@code t20-match-scores}.
 * Partition key is {@code matchId} to guarantee per-match ordering.
 */
@SpringBootApplication
public class T20ScoreProducerApplication {

    public static void main(String[] args) {
        SpringApplication.run(T20ScoreProducerApplication.class, args);
    }
}
