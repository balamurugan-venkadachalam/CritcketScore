package com.crickscore.dashboard.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@Slf4j
public class DashboardController {

    /**
     * Highly cached endpoint for fetching live scores.
     * Cached in Redis for 60 seconds (per application.yml default)
     */
    @GetMapping("/matches/live")
    @Cacheable(value = "live-scores", sync = true)
    public ResponseEntity<List<Map<String, Object>>> getLiveScores() {
        log.info("Fetching live scores (Cache Miss)");
        // TODO: In real app, fetch from DynamoDB / ES here.
        Map<String, Object> fakeMatch1 = Map.of("matchId", "M123", "score", "IND 150/2 (15.0)");
        Map<String, Object> fakeMatch2 = Map.of("matchId", "M124", "score", "AUS 80/1 (10.0)");
        
        return ResponseEntity.ok(List.of(fakeMatch1, fakeMatch2));
    }

    /**
     * Elasticsearch powered endpoint for fuzzy searching matches.
     */
    @GetMapping("/search")
    public ResponseEntity<String> searchMatches(@RequestParam String q) {
        log.info("Executing fuzzy search in Elasticsearch for: {}", q);
        // TODO: Implement Elasticsearch match lookup via NativeSearchQuery
        return ResponseEntity.ok("Search results for: " + q + " (Powered by Elasticsearch)");
    }
    
    /**
     * Player statistics query via Elasticsearch, demonstrating the "Last 1 Year" requirement.
     */
    @GetMapping("/stats/players/{playerId}")
    public ResponseEntity<String> getPlayerStats(@PathVariable String playerId,
                                                 @RequestParam(defaultValue = "career") String timeframe) {
        log.info("Aggregating Elasticsearch data for player: {} over timeframe: {}", playerId, timeframe);
        // TODO: Execute complex bucket_script aggregation against the Matches index
        return ResponseEntity.ok("Computed " + timeframe + " stats for Player ID: " + playerId);
    }
}
