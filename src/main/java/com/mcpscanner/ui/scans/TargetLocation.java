package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.Optional;

public record TargetLocation(String url, String issueName) {

    private static final String NO_ISSUE = "";

    public static TargetLocation of(AuditIssue issue) {
        return new TargetLocation(issue.baseUrl(), issue.name() == null ? NO_ISSUE : issue.name());
    }

    public static TargetLocation host(String origin) {
        return new TargetLocation(origin, NO_ISSUE);
    }

    Optional<String> issue() {
        return Optional.of(issueName).filter(name -> !name.isEmpty());
    }
}
