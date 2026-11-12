package com.mcpscanner.cvetargets.run;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArmObservationTest {

    private static final IssueSummary TRAVERSAL = issue("MCP arbitrary host file read");
    private static final IssueSummary OUTBOUND = issue("MCP outbound request");
    private static final IssueSummary UNKNOWN = issue("Unattributed");

    @Test
    void anUnattributedObservationBelongsWhollyToTheJudgedCheck() {
        ArmObservation observation = new ArmObservation(List.of(TRAVERSAL), 3, 1, new ProbeTally(5, 4), "details");

        assertThat(observation.of("check-a")).isEqualTo(observation);
        assertThat(observation.issuesOtherThan("check-a")).isEmpty();
    }

    @Test
    void anAttributedObservationGivesEachCheckOnlyItsOwnIssuesAndProbes() {
        ArmObservation observation = attributed();

        ArmObservation traversal = observation.of("check-a");

        assertThat(traversal.issues()).containsExactly(TRAVERSAL);
        assertThat(traversal.probes()).isEqualTo(new ProbeTally(5, 4));
        assertThat(traversal.baseRequestCount()).isEqualTo(3);
        assertThat(traversal.auditDetails()).isEqualTo("details");
    }

    @Test
    void aCheckThatSentNothingHasNoIssuesAndNoProbes() {
        ArmObservation silent = attributed().of("check-d");

        assertThat(silent.issues()).isEmpty();
        assertThat(silent.probes()).isEqualTo(ProbeTally.NONE);
    }

    @Test
    void recordsEveryOtherChecksIssuesIncludingUnattributedOnes() {
        assertThat(attributed().issuesOtherThan("check-a")).isEqualTo(Map.of(
                "check-b", List.of(OUTBOUND),
                CheckFindings.UNATTRIBUTED, List.of(UNKNOWN)));
    }

    @Test
    void namesTheIssuesEachCheckMayHaveRaisedUnderASharedName() {
        ArmObservation observation = new ArmObservation(List.of(UNKNOWN), 3, 0, new ProbeTally(9, 7), "details",
                Map.of("check-a", new CheckFindings(List.of(), new ProbeTally(5, 4), List.of(UNKNOWN)),
                        CheckFindings.UNATTRIBUTED, new CheckFindings(List.of(UNKNOWN), ProbeTally.NONE)));

        assertThat(observation.ambiguousFor("check-a")).containsExactly(UNKNOWN);
        assertThat(observation.ambiguousFor("check-b")).isEmpty();
    }

    private static ArmObservation attributed() {
        return new ArmObservation(List.of(TRAVERSAL, OUTBOUND, UNKNOWN), 3, 0, new ProbeTally(9, 7), "details",
                Map.of("check-a", new CheckFindings(List.of(TRAVERSAL), new ProbeTally(5, 4)),
                        "check-b", new CheckFindings(List.of(OUTBOUND), new ProbeTally(4, 3)),
                        "check-c", new CheckFindings(List.of(), new ProbeTally(2, 2)),
                        CheckFindings.UNATTRIBUTED, new CheckFindings(List.of(UNKNOWN), ProbeTally.NONE)));
    }

    private static IssueSummary issue(String name) {
        return new IssueSummary(name, AuditIssueSeverity.HIGH, AuditIssueConfidence.FIRM, "http://127.0.0.1:1",
                "detail");
    }
}
