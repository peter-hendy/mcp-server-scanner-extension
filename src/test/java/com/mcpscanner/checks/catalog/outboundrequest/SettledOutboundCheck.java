package com.mcpscanner.checks.catalog.outboundrequest;

import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.ConsolidationAction;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.SettlingLateIssues;
import com.mcpscanner.checks.shared.collaborator.CollaboratorWatch;
import com.mcpscanner.checks.shared.collaborator.TestWatches;
import com.mcpscanner.http.TempFileCopies;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.BurpNativeJsonRpcFields;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

final class SettledOutboundCheck {

    private final OutboundRequestCheck check;
    private final SettlingLateIssues late;

    private SettledOutboundCheck(OutboundRequestCheck check, SettlingLateIssues late) {
        this.check = check;
        this.late = late;
    }

    static SettledOutboundCheck over(Optional<CollaboratorClient> client, McpEventLog eventLog) {
        return over(TestWatches.immediate(() -> client), eventLog);
    }

    static SettledOutboundCheck keepingIn(TempFileCopies copies, Optional<CollaboratorClient> client) {
        return over(TestWatches.keepingIn(() -> client, copies), McpEventLog.noop());
    }

    private static SettledOutboundCheck over(CollaboratorWatch watch, McpEventLog eventLog) {
        SettlingLateIssues late = new SettlingLateIssues(watch);
        return new SettledOutboundCheck(new OutboundRequestCheck(eventLog, watch, late,
                BurpNativeJsonRpcFields.ABSENT), late);
    }

    List<AuditIssue> audit(HttpRequestResponse base, AuditInsertionPoint point, Http http) {
        List<AuditIssue> returned = check.audit(base, point, http);
        assertThat(returned).as("the audit returns before any Collaborator interaction is read").isEmpty();
        List<AuditIssue> issues = new ArrayList<>(returned);
        issues.addAll(late.settle(check));
        return issues;
    }

    ConsolidationAction consolidate(AuditIssue existing, AuditIssue candidate) {
        return check.consolidate(existing, candidate);
    }

    void clearConnectionState() {
        check.clearConnectionState();
    }

    CheckDescriptor descriptor() {
        return check.descriptor();
    }
}
