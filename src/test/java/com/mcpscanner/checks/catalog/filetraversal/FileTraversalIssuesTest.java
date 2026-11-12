package com.mcpscanner.checks.catalog.filetraversal;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FileTraversalIssuesTest {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void carriesTheFindingCalibrationLocationObservationsAndEvidence() {
        HttpRequestResponse evidence = mock(HttpRequestResponse.class);
        Finding finding = new Finding(FindingKind.FILTER_BYPASS, List.of("seen <here>"), List.of(evidence));
        PathArgumentInsertionPoint point = new PathArgumentInsertionPoint("a");

        AuditIssue issue = new FileTraversalIssues(FileTraversalDescriptor.DESCRIPTOR)
                .issueFor(finding, point.base().request(), point.name());

        assertThat(issue.name()).isEqualTo(FindingKind.FILTER_BYPASS.issueName());
        assertThat(issue.severity()).isEqualTo(FindingKind.FILTER_BYPASS.severity());
        assertThat(issue.confidence()).isEqualTo(FindingKind.FILTER_BYPASS.confidence());
        assertThat(issue.detail()).startsWith("<p>Operation: tools/call read_file. Field: MCP field: params.arguments.path.</p>")
                .contains("seen &lt;here&gt;");
        assertThat(issue.definition().background()).contains("CWE-22");
        assertThat(issue.requestResponses()).containsExactly(evidence);
    }
}
