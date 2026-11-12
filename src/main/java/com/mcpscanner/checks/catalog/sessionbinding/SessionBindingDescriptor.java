package com.mcpscanner.checks.catalog.sessionbinding;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.issue.Cwe;

import java.util.List;

final class SessionBindingDescriptor {

    static final String ID = "session-binding";

    private static final List<String> REFERENCES = List.of(
            "https://modelcontextprotocol.io/specification/2025-06-18/basic/security_best_practices",
            "https://cwe.mitre.org/data/definitions/384.html",
            "https://cwe.mitre.org/data/definitions/639.html");

    static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            ID,
            "MCP session binding across identities",
            "Once per host and compared identity, on a stateful MCP server, replays a captured tool call or "
                    + "resource read with another identity's credentials on the owner's Mcp-Session-Id, with "
                    + "anonymous and own-session controls. Reports a session that returns the owner's result to "
                    + "another identity, or that another identity's credential rebinds.",
            CheckKind.REQUEST,
            AuditIssueSeverity.HIGH,
            AuditIssueConfidence.FIRM,
            true,
            REFERENCES,
            "An MCP session is opened by one authenticated principal. A server that does not bind the session to "
                    + "that principal lets another user who presents the session ID with their own credential run "
                    + "operations in the first user's context, or take the session over.",
            List.of(new Cwe(384, "Session Fixation"),
                    new Cwe(639, "Authorization Bypass Through User-Controlled Key")));

    private SessionBindingDescriptor() {
    }
}
