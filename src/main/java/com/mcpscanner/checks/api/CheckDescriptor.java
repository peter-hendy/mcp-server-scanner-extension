package com.mcpscanner.checks.api;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.issue.Cwe;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CheckDescriptor(
        String id,
        String displayName,
        String description,
        CheckKind kind,
        AuditIssueSeverity headlineSeverity,
        AuditIssueConfidence headlineConfidence,
        boolean defaultEnabled,
        List<String> references,
        Optional<String> burpIssueName,
        String issueBackground,
        List<Cwe> cwes
) {
    public CheckDescriptor {
        requireNonBlank(id, "id");
        requireNonBlank(displayName, "displayName");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(headlineSeverity, "headlineSeverity must not be null");
        Objects.requireNonNull(headlineConfidence, "headlineConfidence must not be null");
        Objects.requireNonNull(references, "references must not be null");
        Objects.requireNonNull(burpIssueName, "burpIssueName must not be null");
        Objects.requireNonNull(issueBackground, "issueBackground must not be null");
        Objects.requireNonNull(cwes, "cwes must not be null");
        references.forEach(reference -> requireNonBlankEntry(reference, "references"));
        burpIssueName.ifPresent(name -> requireNonBlank(name, "burpIssueName"));
        references = List.copyOf(references);
        cwes = List.copyOf(cwes);
    }

    public CheckDescriptor(
            String id,
            String displayName,
            String description,
            CheckKind kind,
            AuditIssueSeverity headlineSeverity,
            AuditIssueConfidence headlineConfidence,
            boolean defaultEnabled,
            List<String> references) {
        this(id, displayName, description, kind, headlineSeverity, headlineConfidence, defaultEnabled,
                references, Optional.empty(), "", List.of());
    }

    public CheckDescriptor(
            String id,
            String displayName,
            String description,
            CheckKind kind,
            AuditIssueSeverity headlineSeverity,
            AuditIssueConfidence headlineConfidence,
            boolean defaultEnabled,
            List<String> references,
            String issueBackground,
            List<Cwe> cwes) {
        this(id, displayName, description, kind, headlineSeverity, headlineConfidence, defaultEnabled,
                references, Optional.empty(), issueBackground, cwes);
    }

    public CheckDescriptor(
            String id,
            String displayName,
            String description,
            CheckKind kind,
            AuditIssueSeverity headlineSeverity,
            AuditIssueConfidence headlineConfidence,
            boolean defaultEnabled,
            List<String> references,
            Optional<String> burpIssueName) {
        this(id, displayName, description, kind, headlineSeverity, headlineConfidence, defaultEnabled,
                references, burpIssueName, "", List.of());
    }

    public String issueName() {
        return burpIssueName.orElse(displayName);
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null or blank");
        }
    }

    private static void requireNonBlankEntry(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not contain null or blank entries: " + value);
        }
    }
}
