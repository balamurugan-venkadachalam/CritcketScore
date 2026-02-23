package com.crickscore.consumer.service;

import com.crickscore.consumer.model.ScoreEvent;
import com.crickscore.consumer.repository.LiveScoreRepository;
import com.crickscore.consumer.repository.ScoreEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ScoreEventService}.
 *
 * <p>
 * Covers:
 * <ul>
 * <li>New event: idempotency passes → save() and update() both called once</li>
 * <li>Duplicate event: idempotency fails → save() and update() never
 * called</li>
 * <li>Repository exception propagates (no silent swallowing)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ScoreEventServiceTest {

    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private ScoreEventRepository scoreEventRepository;
    @Mock
    private LiveScoreRepository liveScoreRepository;

    private ScoreEventService service;

    private static final String MATCH_ID = "IPL-2025-MI-CSK-001";
    private static final String EVENT_ID = "test-event-001";
    private static final String TRACE_ID = "trace-abc-123";

    @BeforeEach
    void setUp() {
        service = new ScoreEventService(
                idempotencyService,
                scoreEventRepository,
                liveScoreRepository,
                new SimpleMeterRegistry());
    }

    // ─── New event ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("New event (not a duplicate)")
    class NewEvent {

        @Test
        @DisplayName("calls save() and update() each exactly once")
        void newEvent_saveAndUpdateCalled() {
            when(idempotencyService.isDuplicate(EVENT_ID)).thenReturn(false);

            service.process(buildEvent(), TRACE_ID);

            verify(scoreEventRepository, times(1)).save(any(ScoreEvent.class));
            verify(liveScoreRepository,  times(1)).update(any(ScoreEvent.class));
        }

        @Test
        @DisplayName("calls save() BEFORE update() (crash-safe ordering)")
        void newEvent_saveBeforeUpdate() {
            when(idempotencyService.isDuplicate(EVENT_ID)).thenReturn(false);

            var inOrder = inOrder(scoreEventRepository, liveScoreRepository);

            service.process(buildEvent(), TRACE_ID);

            inOrder.verify(scoreEventRepository).save(any());
            inOrder.verify(liveScoreRepository).update(any());
        }

        @Test
        @DisplayName("works correctly when traceId is null")
        void newEvent_nullTraceId_doesNotFail() {
            when(idempotencyService.isDuplicate(EVENT_ID)).thenReturn(false);

            service.process(buildEvent(), null);

            verify(scoreEventRepository, times(1)).save(any());
            verify(liveScoreRepository,  times(1)).update(any());
        }
    }

    // ─── Duplicate event ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Duplicate event (already processed)")
    class DuplicateEvent {

        @Test
        @DisplayName("skips save() and update() entirely")
        void duplicateEvent_skipsBothWrites() {
            when(idempotencyService.isDuplicate(EVENT_ID)).thenReturn(true);

            service.process(buildEvent(), TRACE_ID);

            verify(scoreEventRepository, never()).save(any());
            verify(liveScoreRepository,  never()).update(any());
        }
    }

    // ─── Repository failure ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Repository failure")
    class RepositoryFailure {

        @Test
        @DisplayName("save() exception propagates — update() never called, caller handles retry")
        void saveThrows_exceptionPropagates_updateNotCalled() {
            when(idempotencyService.isDuplicate(EVENT_ID)).thenReturn(false);
            doThrow(new RuntimeException("DynamoDB timeout"))
                    .when(scoreEventRepository).save(any());

            assertThatThrownBy(() -> service.process(buildEvent(), TRACE_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DynamoDB timeout");

            verify(liveScoreRepository, never()).update(any());
        }

        @Test
        @DisplayName("update() exception propagates after save() succeeded")
        void updateThrows_exceptionPropagates() {
            when(idempotencyService.isDuplicate(EVENT_ID)).thenReturn(false);
            doThrow(new RuntimeException("DynamoDB update failed"))
                    .when(liveScoreRepository).update(any());

            assertThatThrownBy(() -> service.process(buildEvent(), TRACE_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("DynamoDB update failed");

            // save() was still called once (before the update failure)
            verify(scoreEventRepository, times(1)).save(any());
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ScoreEvent buildEvent() {
        return ScoreEvent.builder()
                .eventId(EVENT_ID)
                .matchId(MATCH_ID)
                .inning(1).over(3).ball(2)
                .team("MI").runs(4).extras(0)
                .wicket(false).totalRuns(67).wickets(2)
                .timestamp(Instant.parse("2025-04-15T14:32:01Z"))
                .build();
    }
}
