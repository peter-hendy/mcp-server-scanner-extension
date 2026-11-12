package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;

final class AdmissionSeverity {

    private AdmissionSeverity() {}

    static AuditIssueSeverity of(AdmissionCategory category, TargetExposure exposure) {
        if (category != AdmissionCategory.HOST_AND_ORIGIN || !exposure.reboundByABrowser()) {
            return AuditIssueSeverity.INFORMATION;
        }
        return exposure.credentialed() ? AuditIssueSeverity.LOW : AuditIssueSeverity.MEDIUM;
    }
}
