package com.mcpscanner.cvetargets.run;

import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import org.awaitility.core.ConditionTimeoutException;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;

public final class PollingReadiness implements ReadinessProbe {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    private final ReadinessAttempt attempt;

    public PollingReadiness(ReadinessAttempt attempt) {
        this.attempt = attempt;
    }

    @Override
    public void awaitReady(ArmEndpoint endpoint, Duration timeout) {
        AtomicReference<String> lastObservation = new AtomicReference<>("no attempt");
        try {
            await().atMost(timeout).pollDelay(Duration.ZERO).pollInterval(POLL_INTERVAL).pollInSameThread()
                    .until(() -> isReady(endpoint, lastObservation));
        } catch (ConditionTimeoutException timedOut) {
            throw new IllegalStateException(endpoint.uri() + " not ready after " + timeout + ": " + lastObservation.get());
        }
    }

    private boolean isReady(ArmEndpoint endpoint, AtomicReference<String> lastObservation) {
        Optional<String> notReady = attempt.notReadyBecause(endpoint);
        notReady.ifPresent(lastObservation::set);
        return notReady.isEmpty();
    }
}
