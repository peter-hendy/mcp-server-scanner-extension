package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.catalog.crossclientresponseleak.TrialOutcome.Verdict;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CrossClientResponseLeakIssuesTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void namesTheEchoedArgumentAndBothMarkersInPlainText() {
        Crossing crossing = new Crossing(new ClientReply("marker-a", 200, "echo:marker-b", Optional.empty()),
                ClientReply.unanswered("marker-b"));
        TrialOutcome outcome = new TrialOutcome(Verdict.CROSSED, "title", Optional.of(crossing));

        AuditIssue issue = new CrossClientResponseLeakIssues(CrossClientResponseLeakDescriptor.DESCRIPTOR)
                .issueFor(outcome, LeakServers.URL).orElseThrow();

        assertThat(issue.detail()).contains("title").contains("marker-a").contains("marker-b")
                .doesNotContain("&lt;");
    }
}
