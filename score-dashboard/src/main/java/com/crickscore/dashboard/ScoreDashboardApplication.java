package com.crickscore.dashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ScoreDashboardApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScoreDashboardApplication.class, args);
    }
}
