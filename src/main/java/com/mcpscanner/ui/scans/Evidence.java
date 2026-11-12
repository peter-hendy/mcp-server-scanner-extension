package com.mcpscanner.ui.scans;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.List;
import java.util.Optional;

final class Evidence {

    private Evidence() {
    }

    static Optional<HttpRequestResponse> first(AuditIssue issue) {
        List<HttpRequestResponse> evidence = issue.requestResponses();
        return evidence == null || evidence.isEmpty() ? Optional.empty() : Optional.of(evidence.get(0));
    }
}
