package com.mcpscanner.checks.registry;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.scancheck.ActiveScanCheck;
import com.mcpscanner.checks.shared.scancontext.ScanContext;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
public interface IssueDestination {

    IssueDestination NONE = (check, issues, scan) -> {
    };

    void add(ActiveScanCheck check, List<AuditIssue> issues, Optional<ScanContext> scan);
}
