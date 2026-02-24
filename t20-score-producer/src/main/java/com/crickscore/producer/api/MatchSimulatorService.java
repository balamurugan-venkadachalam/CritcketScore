package com.crickscore.producer.api;

import com.crickscore.producer.kafka.ScoreEventProducerService;
import com.crickscore.producer.model.ScoreEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Generates realistic T20 ball-by-ball {@link ScoreEvent} sequences for
 * integration testing and demos.
 *
 * <h3>How it works</h3>
 * <ol>
 * <li>Each active simulation runs in a virtual-thread-backed executor.</li>
 * <li>Balls are produced with a configurable inter-ball delay (default 20 s
 * in prod / configurable in tests).</li>
 * <li>Realistic values are selected from weighted probability distributions
 * that mirror real T20 averages (run-rate ~8, boundary frequency ~25%).</li>
 * <li>Each innings is 20 overs × 6 balls (120 legal deliveries), and the
 * simulation runs both innings automatically.</li>
 * </ol>
 *
 * <h3>Concurrency model</h3>
 * A single {@link ScheduledExecutorService} handles all active matches — each
 * match is one periodic task scheduled at {@code delayMs} interval. All state
 * is held in {@link #activeSimulations} (a {@link ConcurrentHashMap}), which is
 * the only shared mutable state.
 *
 * <h3>Stopping</h3>
 * Call {@link #stop(String)} to cancel a match's scheduled task and remove it
 * from the active map. The task cancels cleanly on the next scheduling cycle.
 */
@Slf4j
@Service
public class MatchSimulatorService {

    /** Default delay between consecutive ball deliveries (ms). */
    public static final long DEFAULT_BALL_DELAY_MS = 20_000L;

    /**
     * Weighted run outcomes per ball:
     * 0 (dot) ~35%, 1 ~25%, 2 ~15%, 3 ~5%, 4 (four) ~12%, 6 ~8%.
     */
    private static final int[] RUN_WEIGHTS = { 0, 0, 0, 0, 0, 0, 0, // 35% dots
            1, 1, 1, 1, 1, // 25% singles
            2, 2, 2, // 15% twos
            3, // 5% threes
            4, 4, 4, // 12% fours
            6, 6 // 8% sixes
    };

    /** Probability of a wicket on any given delivery (~5%). */
    private static final double WICKET_PROBABILITY = 0.05;

    private final ScoreEventProducerService producerService;
    private final ScheduledExecutorService scheduler;
    private final Counter simulatedEventsCounter;
    private final Random random = new Random();

    /** matchId → running simulation state. */
    private final Map<String, SimulationState> activeSimulations = new ConcurrentHashMap<>();

    @Autowired
    public MatchSimulatorService(ScoreEventProducerService producerService,
            MeterRegistry meterRegistry) {
        this.producerService = producerService;
        this.scheduler = Executors.newScheduledThreadPool(4,
                Thread.ofVirtual().name("simulator-", 0).factory());
        this.simulatedEventsCounter = Counter.builder("score.events.simulated.total")
                .description("Total score events produced by the match simulator")
                .register(meterRegistry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Starts a ball-by-ball simulation for the given match.
     *
     * @param matchId     unique match identifier used as Kafka partition key.
     * @param ballDelayMs delay between deliveries in milliseconds.
     * @throws IllegalStateException if a simulation for {@code matchId} is already
     *                               running.
     */
    public void start(String matchId, long ballDelayMs) {
        if (activeSimulations.containsKey(matchId)) {
            throw new IllegalStateException(
                    "Simulation already running for matchId=" + matchId);
        }

        SimulationState state = new SimulationState(matchId);
        activeSimulations.put(matchId, state);

        Future<?> task = scheduler.scheduleWithFixedDelay(
                () -> produceBall(matchId, state),
                0L,
                ballDelayMs,
                TimeUnit.MILLISECONDS);

        state.setTask(task);
        log.info("Started simulator: matchId={}, ballDelayMs={}", matchId, ballDelayMs);
    }

    /**
     * Starts a simulation with the default ball delay
     * ({@value DEFAULT_BALL_DELAY_MS} ms).
     */
    public void start(String matchId) {
        start(matchId, DEFAULT_BALL_DELAY_MS);
    }

    /**
     * Stops a running simulation for the given match.
     *
     * @param matchId the match to stop.
     * @return {@code true} if the simulation was found and cancelled; {@code false}
     *         if no simulation was running for this match.
     */
    public boolean stop(String matchId) {
        SimulationState state = activeSimulations.remove(matchId);
        if (state == null) {
            return false;
        }
        Future<?> task = state.getTask();
        if (task != null) {
            task.cancel(false); // polite cancel — let current ball complete
        }
        log.info("Stopped simulator: matchId={}", matchId);
        return true;
    }

    /**
     * Returns an unmodifiable snapshot of the currently active simulation states.
     *
     * @return list of active simulation views (matchId, inning, over, ball,
     *         totalRuns, wickets).
     */
    public List<SimulationStatus> getActiveSimulations() {
        return activeSimulations.entrySet().stream()
                .map(e -> SimulationStatus.from(e.getValue()))
                .toList();
    }

    /** Returns {@code true} if a simulation is running for {@code matchId}. */
    public boolean isRunning(String matchId) {
        return activeSimulations.containsKey(matchId);
    }

    // ── Ball generation ───────────────────────────────────────────────────────

    private void produceBall(String matchId, SimulationState state) {
        try {
            // Check if match is already complete (both innings finished)
            if (state.isMatchComplete()) {
                log.info("Match simulation complete: matchId={}", matchId);
                stop(matchId);
                return;
            }

            // Generate delivery outcome
            int runs = RUN_WEIGHTS[random.nextInt(RUN_WEIGHTS.length)];
            boolean wicket = random.nextDouble() < WICKET_PROBABILITY && state.getWickets() < 10;

            // Advance counters
            state.recordBall(runs, wicket);

            // Build the event
            ScoreEvent event = ScoreEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .matchId(matchId)
                    .inning(state.getInning())
                    .over(state.getOver())
                    .ball(state.getBall())
                    .team("TEAM-" + (state.getInning() == 1 ? "A" : "B"))
                    .runs(runs)
                    .extras(null)
                    .wicket(wicket)
                    .totalRuns(state.getTotalRuns())
                    .wickets(state.getWickets())
                    .timestamp(Instant.now())
                    .build();

            producerService.send(event, "simulator-" + UUID.randomUUID().toString().substring(0, 8));
            simulatedEventsCounter.increment();

            log.debug(
                    "Simulated ball: matchId={}, inning={}, over={}, ball={}, runs={}, wicket={}, totalRuns={}, wickets={}",
                    matchId, state.getInning(), state.getOver(), state.getBall(),
                    runs, wicket, state.getTotalRuns(), state.getWickets());

        } catch (Exception e) {
            log.error("Error producing simulated ball for matchId={}: {}", matchId, e.getMessage(), e);
        }
    }

    // ── Nested state types ────────────────────────────────────────────────────

    /**
     * Mutable simulation state for a single match (not thread-safe beyond
     * single-thread executor per match).
     */
    static class SimulationState {

        private static final int OVERS_PER_INNINGS = 20;
        private static final int BALLS_PER_OVER = 6;
        private static final int MAX_WICKETS = 10;

        private final String matchId;
        private volatile Future<?> task;

        private int inning = 1;
        private int over = 0;
        private int ball = 0; // 0 = before first ball; incremented before each delivery
        private int totalRuns = 0;
        private int wickets = 0;

        SimulationState(String matchId) {
            this.matchId = matchId;
        }

        /**
         * Advances the ball/over/inning counters and accumulates runs and wickets.
         *
         * <p>
         * Over advances after ball 6. Inning advances after over 19 or 10 wickets.
         * After inning 2 completes, {@link #isMatchComplete()} returns {@code true}.
         */
        void recordBall(int runs, boolean wicket) {
            ball++;
            totalRuns += runs;
            if (wicket) {
                wickets++;
            }

            // End of over
            if (ball >= BALLS_PER_OVER) {
                ball = 0;
                over++;
            }

            // End of innings
            if (over >= OVERS_PER_INNINGS || wickets >= MAX_WICKETS) {
                if (inning == 1) {
                    inning = 2;
                    over = 0;
                    ball = 0;
                    totalRuns = 0;
                    wickets = 0;
                    log.info("Simulator: innings 1 complete for matchId={}, starting innings 2", matchId);
                }
                // If inning == 2 is already complete, isMatchComplete() handles it
            }
        }

        boolean isMatchComplete() {
            return inning == 2 && (over >= OVERS_PER_INNINGS || wickets >= MAX_WICKETS);
        }

        String getMatchId() {
            return matchId;
        }

        int getInning() {
            return inning;
        }

        int getOver() {
            return over;
        }

        int getBall() {
            return ball == 0 ? BALLS_PER_OVER : ball;
        }

        int getTotalRuns() {
            return totalRuns;
        }

        int getWickets() {
            return wickets;
        }

        Future<?> getTask() {
            return task;
        }

        void setTask(Future<?> task) {
            this.task = task;
        }
    }

    /**
     * Immutable snapshot of a simulation's current state — safe to return in API
     * responses.
     */
    public record SimulationStatus(
            String matchId,
            int inning,
            int over,
            int ball,
            int totalRuns,
            int wickets,
            boolean complete) {
        static SimulationStatus from(SimulationState s) {
            return new SimulationStatus(
                    s.getMatchId(), s.getInning(), s.getOver(), s.getBall(),
                    s.getTotalRuns(), s.getWickets(), s.isMatchComplete());
        }
    }
}
