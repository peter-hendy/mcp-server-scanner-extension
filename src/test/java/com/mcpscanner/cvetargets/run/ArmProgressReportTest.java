package com.mcpscanner.cvetargets.run;

import com.mcpscanner.testutil.MutableClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArmProgressReportTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-12T12:00:00Z"));
    private final List<String> lines = new ArrayList<>();
    private final ArmProgressReport report = new ArmProgressReport("check-a/target-one/vulnerable", clock,
            Duration.ofSeconds(60), lines::add);

    @Test
    void namesEachPhaseAsTheArmEntersIt() {
        clock.advance(Duration.ofSeconds(95));

        report.phaseStarted(ArmPhase.READINESS);

        assertThat(lines).containsExactly("cve-targets: check-a/target-one/vulnerable: readiness (1m 35s in)");
    }

    @Test
    void reportsAuditProgressAtMostOncePerInterval() {
        ArmObservation observation = new ArmObservation(List.of(), 3, 0, new ProbeTally(40, 38), null);

        report.observed(observation);
        clock.advance(Duration.ofSeconds(30));
        report.observed(observation);
        clock.advance(Duration.ofSeconds(30));
        report.observed(observation);

        assertThat(lines).containsExactly(
                "cve-targets: check-a/target-one/vulnerable: auditing (0s in): 3 base requests, 40 probes sent, 38 reached, 0 issues",
                "cve-targets: check-a/target-one/vulnerable: auditing (1m 0s in): 3 base requests, 40 probes sent, 38 reached, 0 issues");
    }

    @Test
    void appendsWhatTheAuditSaysAboutItsOwnProgress() {
        ArmProgressDetail burp = () -> "Burp audit running, 52000 requests, 1 error";

        report.observed(new ArmObservation(List.of(), 1, 0, ProbeTally.NONE, burp));

        assertThat(lines).singleElement().asString()
                .endsWith("0 issues; Burp audit running, 52000 requests, 1 error");
    }

    @Test
    void passesOnANoteAboutTheArm() {
        report.note("catalog limited to 100 items per kind (tools 124 -> 100); seed requests are still sent");

        assertThat(lines).containsExactly("cve-targets: check-a/target-one/vulnerable: catalog limited to "
                + "100 items per kind (tools 124 -> 100); seed requests are still sent");
    }

    @Test
    void theSilentReportWritesNothing() {
        ArmProgressReport.SILENT.phaseStarted(ArmPhase.AUDIT);
        ArmProgressReport.SILENT.observed(ArmObservation.NONE);

        assertThat(lines).isEmpty();
    }
}
