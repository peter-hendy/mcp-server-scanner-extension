package com.mcpscanner.checks.api;

import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.List;

@FunctionalInterface
public interface LateIssueSettlement {

    LateIssueSettlement NONE = check -> List.of();

    List<AuditIssue> settle(McpCheck check);
}
