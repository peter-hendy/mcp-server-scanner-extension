package com.mcpscanner.checks.catalog.filetraversal;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.issue.IssueBodyBuilder;
import com.mcpscanner.checks.issue.IssueMetadataRenderer;

final class FileTraversalIssues {

    private static final String REMEDIATION = "Resolve every client-supplied path to its canonical form after "
            + "fully decoding it, then require the result to lie inside an explicit allowed root using a "
            + "path-segment comparison, not a string-prefix match. Apply the same rule to absolute paths and "
            + "file:// URIs, compare case-insensitively on case-insensitive filesystems, and resolve symbolic "
            + "links before the containment check.";

    private final CheckDescriptor descriptor;

    FileTraversalIssues(CheckDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    AuditIssue issueFor(Finding finding, HttpRequest baseRequest, String fieldName) {
        FindingKind kind = finding.kind();
        return AuditIssue.auditIssue(kind.issueName(), detail(finding, baseRequest, fieldName), REMEDIATION,
                baseRequest.url(), kind.severity(), kind.confidence(),
                IssueMetadataRenderer.background(descriptor.issueBackground(), descriptor.cwes(),
                        descriptor.references()),
                null, kind.severity(), finding.evidence().toArray(HttpRequestResponse[]::new));
    }

    private static String detail(Finding finding, HttpRequest baseRequest, String fieldName) {
        return new IssueBodyBuilder()
                .paragraph("Operation: " + OperationLabel.of(baseRequest) + ". Field: " + fieldName + ".")
                .paragraph(finding.meaning())
                .findings(finding.observations())
                .build();
    }
}
