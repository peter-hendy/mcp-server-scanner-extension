package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.issue.Cwe;

import java.util.List;

final class UnauthenticatedRequestExecutionDescriptor {

    private static final String ID = "unauthenticated-request-execution";

    private static final List<String> REFERENCES = List.of(
            "https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization",
            "https://modelcontextprotocol.io/specification/2025-06-18/basic/security_best_practices",
            "https://modelcontextprotocol.io/specification/2025-06-18/basic/transports#session-management",
            "https://cwe.mitre.org/data/definitions/306.html",
            "https://cwe.mitre.org/data/definitions/288.html",
            "https://cwe.mitre.org/data/definitions/287.html",
            "https://cwe.mitre.org/data/definitions/290.html");

    static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            ID,
            "MCP unauthenticated request execution",
            "Replays every MCP request, notification and list without credentials or a session, then, where "
                    + "the server refuses that, with path, header, Host, Content-Type, body, credential and "
                    + "session variants, and with fabricated credentials. Reports a request that succeeds with the "
                    + "same result as the scanner's own request. Also replays the scanner's own request with "
                    + "client-address headers to find a source-address restriction that trusts them.",
            CheckKind.REQUEST,
            AuditIssueSeverity.HIGH,
            AuditIssueConfidence.FIRM,
            true,
            REFERENCES,
            "MCP servers expose tools and data over JSON-RPC. A request that runs without authentication, or "
                    + "with an altered request the authentication layer does not recognise, gives any network "
                    + "caller the scanner's access.",
            List.of(new Cwe(306, "Missing Authentication for Critical Function"),
                    new Cwe(288, "Authentication Bypass Using an Alternate Path or Channel"),
                    new Cwe(287, "Improper Authentication"),
                    new Cwe(290, "Authentication Bypass by Spoofing")));

    private UnauthenticatedRequestExecutionDescriptor() {
    }
}
