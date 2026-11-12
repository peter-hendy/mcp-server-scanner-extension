package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.AuditSnapshot;
import com.mcpscanner.automation.CheckAccessCommand;
import com.mcpscanner.automation.CheckAccessStatus;
import com.mcpscanner.automation.LaunchedScan;
import com.mcpscanner.automation.ScanAllCommand;
import com.mcpscanner.testutil.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditCompletionTest {

    private static final Duration POLL = Duration.ofSeconds(5);
    private static final Duration QUIET = Duration.ofSeconds(30);

    private static final Instant START = Instant.parse("2026-11-09T12:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private final AuditCompletion completion = new AuditCompletion(clock::advance, clock, POLL, QUIET);

    @Test
    void returnsOnceBurpSaysTheAuditFinished() {
        CompletedAudit audit = completion.await(scripted(snapshot("scanning", 10), snapshot("Finished", 40)),
                () -> false);

        assertThat(audit.end()).isEqualTo(CompletedAudit.AuditEnd.FINISHED);
        assertThat(audit.snapshot().requestCount()).isEqualTo(40);
    }

    @Test
    void keepsWaitingWhileTheRequestCountGrows() {
        CompletedAudit audit = completion.await(scripted(snapshot("scanning", 1), snapshot("scanning", 2),
                snapshot("scanning", 3), snapshot("scanning", 4), snapshot("scanning", 5), snapshot("scanning", 6),
                snapshot("scanning", 7), snapshot("finished", 8)), () -> false);

        assertThat(audit.end()).isEqualTo(CompletedAudit.AuditEnd.FINISHED);
    }

    @Test
    void treatsAnAuditThatSentNothingForTheQuietPeriodAsQuiescent() {
        CompletedAudit audit = completion.await(constant(snapshot("scanning", 12)), () -> false);

        assertThat(audit.end()).isEqualTo(CompletedAudit.AuditEnd.QUIESCENT);
        assertThat(Duration.between(START, clock.instant())).isGreaterThanOrEqualTo(QUIET);
    }

    @Test
    void stopsWaitingWhenTheArmIsTornDown() {
        assertThatThrownBy(() -> completion.await(constant(snapshot("scanning", 12)), () -> true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("torn down");
    }

    @Test
    void failsWhenBurpHasNoLaunchedAudit() {
        assertThatThrownBy(() -> completion.await(noAudit(), () -> false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no launched audit");
    }

    private static AuditSnapshot snapshot(String status, int requests) {
        return new AuditSnapshot(status, requests, 0, 4, 0, 0, List.of(), 0, Map.of());
    }

    private static BurpAutomation scripted(AuditSnapshot... snapshots) {
        Deque<AuditSnapshot> remaining = new ArrayDeque<>(List.of(snapshots));
        return automation(() -> Optional.of(remaining.size() > 1 ? remaining.poll() : remaining.peek()));
    }

    private static BurpAutomation constant(AuditSnapshot snapshot) {
        return automation(() -> Optional.of(snapshot));
    }

    private static BurpAutomation noAudit() {
        return automation(Optional::empty);
    }

    private static BurpAutomation automation(java.util.function.Supplier<Optional<AuditSnapshot>> audits) {
        return new BurpAutomation() {
            @Override
            public LaunchedScan scanAll(ScanAllCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<AuditSnapshot> audit() {
                return audits.get();
            }

            @Override
            public CheckAccessStatus checkAccess(CheckAccessCommand command) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<CheckAccessStatus> checkAccessStatus() {
                return Optional.empty();
            }
        };
    }
}
