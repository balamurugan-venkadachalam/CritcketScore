package com.crickscore.consumer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * No-op stub implementation of {@link DeadLetterNotificationService}.
 *
 * <p>
 * Active on {@code local} and {@code test} profiles only.
 * Logs the DLT event details instead of sending an SNS notification,
 * so the full retry/DLT pipeline can be exercised without AWS connectivity.
 *
 * <p>
 * The real SNS implementation is added in TASK-14 (Observability) once
 * the SNS topic ARN is provisioned by the CDK stack.
 */
@Slf4j
@Service
@Profile({ "local", "test" })
public class NoOpDeadLetterNotificationService implements DeadLetterNotificationService {

    @Override
    public void notify(String matchId, int partition, long offset,
            String payload, String errorFqcn, String errorMessage) {
        log.warn("[NoOp] DLT notification would be sent to SNS: " +
                "matchId={}, partition={}, offset={}, error=[{}] {}",
                matchId, partition, offset, errorFqcn, errorMessage);
    }
}
