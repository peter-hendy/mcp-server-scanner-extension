package com.mcpscanner.checks.catalog.siblingrouteauthparity;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.issue.Cwe;

import java.util.List;

final class SiblingRouteAuthParityDescriptor {

    private static final String ID = "sibling-route-auth-parity";

    private static final List<String> REFERENCES = List.of(
            "https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization",
            "https://modelcontextprotocol.io/specification/2024-11-05/basic/transports#http-with-sse",
            "https://cwe.mitre.org/data/definitions/288.html",
            "https://cwe.mitre.org/data/definitions/306.html");

    static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            ID,
            "MCP sibling route authentication parity",
            "Once per host, confirms that the MCP endpoint refuses a catalog listing without credentials, then "
                    + "replays it without credentials on the routes MCP servers commonly mount beside the endpoint "
                    + "(mcp_message, messages, messages/, message, sse, mcp, mcp/; on Streamable HTTP a route "
                    + "unauthenticated-request-execution sweeps is reported only when its nonexistent-route "
                    + "control would make that check suppress it) and, on the legacy SSE "
                    + "transport, on the message route the server advertised. Reports a route that returns the "
                    + "same result as the authenticated request.",
            CheckKind.HOST,
            AuditIssueSeverity.LOW,
            AuditIssueConfidence.FIRM,
            true,
            REFERENCES,
            "An MCP server often serves the same JSON-RPC handler on more than one route, for example a "
                    + "Streamable HTTP endpoint beside a legacy SSE message endpoint. When authentication is "
                    + "attached to one route and not to its siblings, the unprotected route answers requests the "
                    + "main route refuses. This check shows it with a catalog listing only.",
            List.of(new Cwe(288, "Authentication Bypass Using an Alternate Path or Channel"),
                    new Cwe(306, "Missing Authentication for Critical Function")));

    private SiblingRouteAuthParityDescriptor() {
    }
}
