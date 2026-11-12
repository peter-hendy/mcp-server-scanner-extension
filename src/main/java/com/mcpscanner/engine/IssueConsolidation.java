package com.mcpscanner.engine;

import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

@FunctionalInterface
public interface IssueConsolidation {

    ConsolidationAction consolidate(AuditIssue existing, AuditIssue candidate);
}
