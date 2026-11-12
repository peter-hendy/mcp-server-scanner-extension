package com.mcpscanner.checks.api;

import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.List;

@FunctionalInterface
public interface LateIssueSink {

    LateIssueSink DISCARD = (check, issues) -> { };

    void report(McpCheck check, List<AuditIssue> issues);
}
