package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;

record ScopeExpansionFinding(ScopeProbe probe, RefreshEvidence evidence) {

    AuditIssueConfidence confidence() {
        return probe.original().declaredByAuthorizationServer() && probe.refresh().usability() == Usability.USABLE
                ? AuditIssueConfidence.FIRM : AuditIssueConfidence.TENTATIVE;
    }
}
