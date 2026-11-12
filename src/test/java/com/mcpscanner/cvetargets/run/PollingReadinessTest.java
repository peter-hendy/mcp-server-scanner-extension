package com.mcpscanner.cvetargets.run;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PollingReadinessTest {

    private final ArmEndpoint endpoint = new ArmEndpoint(URI.create("http://127.0.0.1:1/mcp"),
            new NoAuthStrategy(), TransportType.STREAMABLE_HTTP);

    @Test
    void returnsOnceAnAttemptFindsTheArmReady() {
        AtomicInteger attempts = new AtomicInteger();

        new PollingReadiness(ignored -> attempts.incrementAndGet() < 3 ? Optional.of("starting") : Optional.empty())
                .awaitReady(endpoint, Duration.ofSeconds(10));

        assertThat(attempts).hasValue(3);
    }

    @Test
    void namesTheLastObservationWhenTheArmNeverBecomesReady() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> new PollingReadiness(ignored -> Optional.of("refused #" + attempts.incrementAndGet()))
                .awaitReady(endpoint, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("http://127.0.0.1:1/mcp not ready after PT1S: refused #" + attempts.get());
    }
}
