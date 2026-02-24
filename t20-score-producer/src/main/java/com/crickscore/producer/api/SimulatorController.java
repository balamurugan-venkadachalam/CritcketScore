package com.crickscore.producer.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the match simulator endpoints.
 *
 * <h3>Endpoints</h3>
 * <ul>
 * <li>{@code POST /api/v1/simulate} — start a new simulation for one or more
 * matches.</li>
 * <li>{@code DELETE /api/v1/simulate/{matchId}} — stop a running
 * simulation.</li>
 * <li>{@code GET /api/v1/simulate/status} — list all active simulations with
 * current score state.</li>
 * </ul>
 *
 * <h3>Use cases</h3>
 * <ul>
 * <li>Manual testing: trigger realistic event sequences without a real
 * scoring feed.</li>
 * <li>Load testing: spin up N concurrent match simulations to stress-test the
 * Kafka → consumer pipeline.</li>
 * <li>Demo scenarios: produce a live-looking score stream for UI dashboards.
 * </li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/simulate")
public class SimulatorController {

    private final MatchSimulatorService simulatorService;

    @Autowired
    public SimulatorController(MatchSimulatorService simulatorService) {
        this.simulatorService = simulatorService;
    }

    /**
     * Starts a simulation for one or more matches.
     *
     * <h3>Request body</h3>
     * 
     * <pre>
     * {
     *   "matchIds":    ["IPL-2025-MI-CSK-001", "IPL-2025-RCB-KKR-001"],
     *   "ballDelayMs": 5000
     * }
     * </pre>
     * 
     * {@code ballDelayMs} is optional; defaults to
     * {@value MatchSimulatorService#DEFAULT_BALL_DELAY_MS} ms.
     *
     * @return {@code 202 Accepted} with the list of started match IDs.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> start(
            @RequestBody SimulateRequest request) {

        long delay = request.ballDelayMs() != null
                ? request.ballDelayMs()
                : MatchSimulatorService.DEFAULT_BALL_DELAY_MS;

        List<String> started = request.matchIds().stream()
                .filter(matchId -> {
                    if (simulatorService.isRunning(matchId)) {
                        log.warn("Skipping already-running simulation: matchId={}", matchId);
                        return false;
                    }
                    simulatorService.start(matchId, delay);
                    return true;
                })
                .toList();

        log.info("Started {} simulation(s): {} with ballDelayMs={}", started.size(), started, delay);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(Map.of(
                        "started", started,
                        "ballDelayMs", delay,
                        "message", "Simulation started for %d match(es)".formatted(started.size())));
    }

    /**
     * Stops the running simulation for a specific match.
     *
     * @param matchId the match to stop.
     * @return {@code 200 OK} if stopped, {@code 404 Not Found} if not running.
     */
    @DeleteMapping("/{matchId}")
    public ResponseEntity<Map<String, String>> stop(@PathVariable String matchId) {
        boolean stopped = simulatorService.stop(matchId);

        if (!stopped) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "matchId", matchId,
                            "message", "No active simulation found for matchId=" + matchId));
        }

        log.info("Stopped simulation via API: matchId={}", matchId);
        return ResponseEntity.ok(Map.of(
                "matchId", matchId,
                "message", "Simulation stopped"));
    }

    /**
     * Lists all currently active simulations with their running score state.
     *
     * @return {@code 200 OK} with a list of
     *         {@link MatchSimulatorService.SimulationStatus}.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        List<MatchSimulatorService.SimulationStatus> active = simulatorService.getActiveSimulations();

        return ResponseEntity.ok(Map.of(
                "activeCount", active.size(),
                "simulations", active));
    }

    // ── Request DTOs ──────────────────────────────────────────────────────────

    /**
     * Request body for {@code POST /api/v1/simulate}.
     *
     * @param matchIds    list of match IDs to simulate (required, non-empty).
     * @param ballDelayMs optional inter-ball delay in ms (defaults to
     *                    {@value MatchSimulatorService#DEFAULT_BALL_DELAY_MS}).
     */
    public record SimulateRequest(
            List<String> matchIds,
            Long ballDelayMs) {
    }
}
