package com.mcpscanner.checks.api;

import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

public sealed interface McpCheck permits InsertionPointCheck, RequestCheck, HostCheck {

    CheckDescriptor descriptor();

    <R> R accept(CheckVisitor<R> visitor);

    default boolean requiresReply() {
        return false;
    }

    default ConsolidationAction consolidate(AuditIssue existingIssue, AuditIssue newIssue) {
        return existingIssue.name().equals(newIssue.name())
                ? ConsolidationAction.KEEP_EXISTING
                : ConsolidationAction.KEEP_BOTH;
    }
}
