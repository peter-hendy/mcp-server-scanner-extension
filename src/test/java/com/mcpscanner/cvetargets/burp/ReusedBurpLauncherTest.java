package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.AuditSnapshot;
import com.mcpscanner.automation.CheckAccessCommand;
import com.mcpscanner.automation.CheckAccessStatus;
import com.mcpscanner.automation.LaunchedScan;
import com.mcpscanner.automation.ReportedIssue;
import com.mcpscanner.automation.ScanAllCommand;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReusedBurpLauncherTest {

    private final List<FakeBurp> started = new ArrayList<>();
    private final ReusedBurpLauncher launcher = new ReusedBurpLauncher((label, origin, abandoned) -> {
        FakeBurp burp = new FakeBurp();
        started.add(burp);
        return burp;
    }, 3);

    @Test
    void servesConsecutiveArmsOnDifferentOriginsFromOneBurp() {
        armFinishes("http://127.0.0.1:1001");
        try (BurpSession lease = launcher.start("arm", "http://127.0.0.1:1002", () -> false)) {
            assertThat(lease.automation()).isSameAs(started.get(0));
        }

        assertThat(started).hasSize(1);
        assertThat(started.get(0).stopped).isFalse();
    }

    @Test
    void startsAFreshBurpWhenAnOriginRepeatsSoAnEarlierArmsIssuesNeverCount() {
        armFinishes("http://127.0.0.1:1001");
        armFinishes("http://127.0.0.1:1001");

        assertThat(started).hasSize(2);
        assertThat(started.get(0).stopped).isTrue();
    }

    @Test
    void startsAFreshBurpAfterItsQuotaOfArms() {
        for (int arm = 0; arm < 4; arm++) {
            armFinishes("http://127.0.0.1:100" + arm);
        }

        assertThat(started).hasSize(2);
        assertThat(started.get(0).stopped).isTrue();
    }

    @Test
    void stopsABurpWhoseArmEndedBeforeItsAuditFinished() {
        try (BurpSession lease = launcher.start("arm", "http://127.0.0.1:1001", () -> false)) {
            started.get(0).status = "scanning";
        }

        assertThat(started.get(0).stopped).isTrue();
        armFinishes("http://127.0.0.1:1002");
        assertThat(started).hasSize(2);
    }

    @Test
    void neverReusesABurpAfterAnArmTheExtensionRaisedAnIssueOnSoNoIssueIsEverConsolidatedAway() {
        try (BurpSession lease = launcher.start("arm", "http://127.0.0.1:1001", () -> false)) {
            started.get(0).extensionIssue = true;
        }

        assertThat(started.get(0).stopped).isTrue();
        armFinishes("http://127.0.0.1:1002");
        assertThat(started).hasSize(2);
    }

    @Test
    void givesAConcurrentArmItsOwnBurpWhileTheWarmOneIsLeased() {
        BurpSession first = launcher.start("arm", "http://127.0.0.1:1001", () -> false);
        BurpSession second = launcher.start("arm", "http://127.0.0.1:1002", () -> false);

        assertThat(started).hasSize(2);
        assertThat(second).isSameAs(started.get(1));
        second.close();
        assertThat(started.get(1).stopped).isTrue();
        assertThat(started.get(0).stopped).isFalse();
        first.close();
        armFinishes("http://127.0.0.1:1003");
        assertThat(started).hasSize(2);
    }

    @Test
    void aLateReleaseFromATimedOutArmNeverStopsTheBurpAnotherArmHolds() {
        BurpSession timedOut = launcher.start("arm", "http://127.0.0.1:1001", () -> false);
        started.get(0).status = "scanning";
        BurpSession next = launcher.start("arm", "http://127.0.0.1:1002", () -> false);

        timedOut.close();

        assertThat(started.get(1).stopped).isFalse();
        next.close();
    }

    @Test
    void stopsABurpThatNoLongerAnswers() {
        armFinishes("http://127.0.0.1:1001");
        started.get(0).answering = false;

        armFinishes("http://127.0.0.1:1002");

        assertThat(started).hasSize(2);
        assertThat(started.get(0).stopped).isTrue();
    }

    @Test
    void closingTheLauncherStopsTheWarmBurp() {
        armFinishes("http://127.0.0.1:1001");

        launcher.close();

        assertThat(started.get(0).stopped).isTrue();
    }

    private void armFinishes(String origin) {
        launcher.start("arm", origin, () -> false).close();
    }

    private static final class FakeBurp implements BurpSession, BurpAutomation {
        private boolean stopped;
        private boolean answering = true;
        private String status = "finished";
        private boolean extensionIssue;

        @Override
        public BurpAutomation automation() {
            return this;
        }

        @Override
        public void close() {
            stopped = true;
        }

        @Override
        public LaunchedScan scanAll(ScanAllCommand command) {
            return new LaunchedScan(0);
        }

        @Override
        public Optional<AuditSnapshot> audit() {
            if (!answering) {
                throw new java.io.UncheckedIOException(new java.io.IOException("connection refused"));
            }
            List<ReportedIssue> issues = extensionIssue
                    ? List.of(new ReportedIssue("ours", "HIGH", "FIRM", "http://127.0.0.1:1001", "", true, List.of("check")))
                    : List.of();
            return Optional.of(new AuditSnapshot(status, 0, 0, 0, 0, 0, issues, 0, Map.of()));
        }

        @Override
        public CheckAccessStatus checkAccess(CheckAccessCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CheckAccessStatus> checkAccessStatus() {
            return Optional.empty();
        }
    }
}
