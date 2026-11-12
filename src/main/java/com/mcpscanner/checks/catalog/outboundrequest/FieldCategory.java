package com.mcpscanner.checks.catalog.outboundrequest;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.issue.IssueRating;

enum FieldCategory {

    REQUEST_FIELD(
            "MCP server outbound request",
            new IssueRating(AuditIssueSeverity.MEDIUM, AuditIssueConfidence.FIRM),
            new IssueRating(AuditIssueSeverity.LOW, AuditIssueConfidence.TENTATIVE),
            "This shows that the server, or a component that handled the request after it, contacted a host the "
                    + "caller chose. It does not show that internal addresses are reachable, what the server does "
                    + "with the response, or that the operation is sensitive.",
            "Do not let MCP request fields or headers choose the host the server connects to. Take upstream "
                    + "destinations from server-side configuration, or check caller-supplied URLs against an "
                    + "explicit host allowlist after parsing and before any DNS lookup. Never attach server or "
                    + "caller credentials to a caller-chosen host."),
    FETCH_BY_DESIGN(
            "MCP server outbound request by design",
            new IssueRating(AuditIssueSeverity.INFORMATION, AuditIssueConfidence.FIRM),
            new IssueRating(AuditIssueSeverity.INFORMATION, AuditIssueConfidence.TENTATIVE),
            "The tool's name and this URL argument say that requesting the URL the caller supplies is the tool's "
                    + "purpose, so the request itself is expected. This does not show that internal addresses are "
                    + "reachable or that the server sends credentials with the request.",
            "If callers should not reach internal destinations, send the tool's requests through an egress path "
                    + "that refuses private, loopback and link-local destinations and does not follow redirects to "
                    + "them. Never attach server or caller credentials to the requested URL."),
    CLIENT_METADATA(
            "MCP client metadata URL fetched by server",
            new IssueRating(AuditIssueSeverity.INFORMATION, AuditIssueConfidence.FIRM),
            new IssueRating(AuditIssueSeverity.INFORMATION, AuditIssueConfidence.TENTATIVE),
            "Client metadata such as clientInfo.websiteUrl and icons is descriptive, and a server may fetch it on "
                    + "purpose, for example to show an icon. This shows the server contacts a host the client "
                    + "names. It does not show that internal addresses are reachable.",
            "Display client metadata URLs without fetching them, or fetch them through an egress path that refuses "
                    + "private, loopback and link-local destinations and does not follow redirects to them."),
    OAUTH_CLIENT_METADATA(
            "OAuth client metadata URL fetched by authorization server",
            new IssueRating(AuditIssueSeverity.INFORMATION, AuditIssueConfidence.FIRM),
            new IssueRating(AuditIssueSeverity.INFORMATION, AuditIssueConfidence.TENTATIVE),
            "Authorization servers fetch some client metadata by design, such as jwks_uri, "
                    + "sector_identifier_uri, request_uri and Client ID Metadata Documents. This shows the server "
                    + "fetched a URL the registering client chose. It does not show that internal addresses are "
                    + "reachable.",
            "Fetch client-supplied metadata URLs only over HTTPS, through an egress path that refuses private, "
                    + "loopback and link-local destinations and does not follow redirects to them. Where possible, "
                    + "restrict the hosts a client may name.");

    private static final IssueRating CREDENTIAL_FORWARDED = new IssueRating(AuditIssueSeverity.HIGH, AuditIssueConfidence.FIRM);

    private final String issueName;
    private final IssueRating httpRating;
    private final IssueRating lookupRating;
    private final String scope;
    private final String remediation;

    FieldCategory(String issueName, IssueRating httpRating, IssueRating lookupRating, String scope, String remediation) {
        this.issueName = issueName;
        this.httpRating = httpRating;
        this.lookupRating = lookupRating;
        this.scope = scope;
        this.remediation = remediation;
    }

    String issueName() {
        return issueName;
    }

    String scope() {
        return scope;
    }

    String remediation() {
        return remediation;
    }

    FieldCategory reported(boolean credentialForwarded) {
        return credentialForwarded && this == FETCH_BY_DESIGN ? REQUEST_FIELD : this;
    }

    IssueRating rating(InteractionStrength strength, boolean credentialForwarded) {
        if (strength == InteractionStrength.DNS_LOOKUP_ONLY) {
            return lookupRating;
        }
        return credentialForwarded ? CREDENTIAL_FORWARDED : httpRating;
    }
}
