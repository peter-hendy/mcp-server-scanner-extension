package com.mcpscanner.checks.catalog.filetraversal;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

import java.util.List;

enum FindingKind {
    FILTER_BYPASS("MCP file path traversal (filter bypass)", AuditIssueSeverity.HIGH, AuditIssueConfidence.FIRM,
            "The field rejected the literal path %s but returned the same host file's contents for an encoded, "
                    + "obfuscated or NUL-terminated spelling of it. The server enforces a path restriction, such as "
                    + "a traversal filter or a file-extension allowlist, and the restriction can be bypassed."),
    FILE_READ("MCP file path traversal", AuditIssueSeverity.HIGH, AuditIssueConfidence.TENTATIVE,
            "The field returned the contents of a host system file when given a relative traversal path. "
                    + "This proves the field drives a file read outside any application directory. It does not "
                    + "prove the tool was meant to be confined: a tool deliberately exposing the whole filesystem "
                    + "behaves the same way, so confirm the intended root."),
    HOST_FILE_READ("MCP arbitrary host file read", AuditIssueSeverity.HIGH, AuditIssueConfidence.TENTATIVE,
            "The field returned the contents of a host system file when given its absolute path, so the field "
                    + "reads files anywhere on the server host. No traversal was involved. A tool deliberately "
                    + "exposing the whole filesystem behaves the same way, so confirm the intended root."),
    PARTIAL_FILE_DISCLOSURE("MCP file path traversal (partial file disclosure)", AuditIssueSeverity.MEDIUM,
            AuditIssueConfidence.TENTATIVE,
            "The field returned the opening bytes of /etc/passwd, typically inside a parse or format error. The "
                    + "server opened the file; only part of it was returned. Confirm the intended root."),
    RESTRICTED_PATH_BYPASS("MCP restricted path bypass (case variant)", AuditIssueSeverity.MEDIUM,
            AuditIssueConfidence.FIRM,
            "The field rejected %s but returned Git repository configuration for a differently cased "
                    + "spelling of the same path. The server's path restriction is case-sensitive while the "
                    + "filesystem is not."),
    REPOSITORY_METADATA_READ("MCP repository metadata read", AuditIssueSeverity.INFORMATION,
            AuditIssueConfidence.TENTATIVE,
            "The field returned Git repository configuration for a .git/config path. Repository metadata can "
                    + "disclose remote URLs and credentials. Whether the tool may serve it depends on its "
                    + "intended policy."),
    ROOT_BOUNDARY_BYPASS("MCP file root boundary bypass (prefix sibling)", AuditIssueSeverity.LOW,
            AuditIssueConfidence.TENTATIVE,
            "The field denied a path outside its allowed root and named the root. A missing path in a sibling "
                    + "directory whose name merely starts with the root's name was not denied: the server reported "
                    + "a filesystem not-found error instead. This is the error pattern of a string-prefix "
                    + "containment check (CVE-2025-53110). No file content was read, and a server that rewrites "
                    + "its errors could produce the same pattern.");

    private final String issueName;
    private final AuditIssueSeverity severity;
    private final AuditIssueConfidence confidence;
    private final String meaning;

    FindingKind(String issueName, AuditIssueSeverity severity, AuditIssueConfidence confidence, String meaning) {
        this.issueName = issueName;
        this.severity = severity;
        this.confidence = confidence;
        this.meaning = meaning;
    }

    String issueName() {
        return issueName;
    }

    AuditIssueSeverity severity() {
        return severity;
    }

    AuditIssueConfidence confidence() {
        return confidence;
    }

    String meaning(List<String> rejectedLiterals) {
        return meaning.formatted(String.join(" and ", rejectedLiterals));
    }
}
