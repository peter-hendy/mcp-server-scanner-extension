package com.mcpscanner.access.comparison;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import com.mcpscanner.checks.api.AccessFinding;
import com.mcpscanner.checks.api.Principal;

import java.util.List;
import java.util.Objects;

record Finding(FindingKind kind, String family, AuditIssueConfidence confidence, String observation,
               Principal principal, List<HttpRequestResponse> evidence) {

    Finding {
        evidence = evidence.stream().filter(Objects::nonNull).toList();
    }

    boolean filed() {
        return kind == FindingKind.OPERATION_AVAILABLE || firm();
    }

    AccessFinding accessFinding(Principal owner) {
        return new AccessFinding(accessKind(), FindingSummary.of(this, owner));
    }

    boolean firm() {
        return confidence != AuditIssueConfidence.TENTATIVE;
    }

    private AccessFinding.Kind accessKind() {
        if (!filed()) {
            return AccessFinding.Kind.POSSIBLE_OBJECT_LEVEL;
        }
        return switch (kind) {
            case OPERATION_AVAILABLE -> AccessFinding.Kind.OPERATION_LEVEL;
            case RESULT_AVAILABLE -> AccessFinding.Kind.OBJECT_LEVEL;
        };
    }
}
