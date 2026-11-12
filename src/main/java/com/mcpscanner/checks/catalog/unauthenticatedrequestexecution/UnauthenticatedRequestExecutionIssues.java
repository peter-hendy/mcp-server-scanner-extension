package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.issue.IssueBodyBuilder;
import com.mcpscanner.checks.issue.IssueMetadataRenderer;
import com.mcpscanner.checks.issue.IssueRating;
import com.mcpscanner.checks.shared.probe.McpMethodClass;
import com.mcpscanner.checks.shared.stream.StreamEvidenceNote;

import java.util.List;

final class UnauthenticatedRequestExecutionIssues {

    private final List<String> references;
    private final Calibration calibration = new Calibration();

    UnauthenticatedRequestExecutionIssues(List<String> references) {
        this.references = List.copyOf(references);
    }

    AuditIssue render(Finding finding) {
        IssueRating rating = calibration.rate(finding);
        FindingKind kind = finding.kind();
        return AuditIssue.auditIssue(kind.issueName(), detail(finding),
                kind.remediation(), finding.subject().seed().url(), rating.severity(), rating.confidence(),
                IssueMetadataRenderer.background(kind.background(), kind.cwes(), references), "",
                rating.severity(), finding.evidence().toArray(HttpRequestResponse[]::new));
    }

    private static String detail(Finding finding) {
        return finding.kind().concernsEventStream() ? streamDetail(finding) : requestDetail(finding);
    }

    private static String streamDetail(Finding finding) {
        return new IssueBodyBuilder()
                .paragraph("Variant: " + finding.variantName() + valueSuffix(finding.variantValue()) + ".")
                .paragraph("The first request in the evidence opened the event stream with the configured "
                        + "credentials and the second opened it again without them, each in a fresh MCP session of "
                        + "its own. The server answered both with a text/event-stream body and kept the stream open.")
                .paragraph(StreamEvidenceNote.OPENED_OUTSIDE_BURP)
                .build();
    }

    private static String requestDetail(Finding finding) {
        AuditSubject subject = finding.subject();
        return new IssueBodyBuilder()
                .paragraph("JSON-RPC method: " + subject.message().method()
                        + " (" + subject.methodClass().name().toLowerCase() + ").")
                .paragraph("Variant: " + finding.variantName() + valueSuffix(finding.variantValue()) + ".")
                .paragraph(subject.credentialsConfigured()
                        ? "The scan was configured with credentials; the authenticated request is the first "
                                + "request in the evidence."
                        : "The scan was configured without credentials, so the baseline request was itself "
                                + "unauthenticated.")
                .paragraph(comparison(subject.methodClass()))
                .build();
    }

    private static String comparison(McpMethodClass methodClass) {
        return switch (methodClass) {
            case OPERATION -> "The probe returned the same operation result value as the baseline.";
            case CATALOG, PROTOCOL -> "The probe returned a successful result with the same structure as the baseline.";
            case NOTIFICATION -> "The notification was accepted with the same HTTP status as the baseline; "
                    + "notifications have no reply, so the effect itself is not observed.";
        };
    }

    private static String valueSuffix(String value) {
        return value == null || value.isEmpty() ? "" : " = " + value;
    }
}
