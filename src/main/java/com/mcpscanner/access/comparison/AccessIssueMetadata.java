package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.issue.Cwe;

import java.util.List;

final class AccessIssueMetadata {

    static final List<String> REFERENCES = List.of(
            "https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization",
            "https://modelcontextprotocol.io/specification/2025-06-18/basic/security_best_practices",
            "https://cwe.mitre.org/data/definitions/639.html",
            "https://cwe.mitre.org/data/definitions/285.html");

    static final List<Cwe> CWES = List.of(new Cwe(639, "Authorization Bypass Through User-Controlled Key"),
            new Cwe(285, "Improper Authorization"));

    private AccessIssueMetadata() {
    }
}
