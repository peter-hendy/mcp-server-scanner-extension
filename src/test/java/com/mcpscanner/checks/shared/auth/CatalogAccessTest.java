package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.issue.IssueRating;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogAccessTest {

    @ParameterizedTest
    @EnumSource(CatalogAccess.class)
    void catalogOnlyEvidenceIsLowAndFirm(CatalogAccess access) {
        assertThat(access.rating()).isEqualTo(new IssueRating(AuditIssueSeverity.LOW, AuditIssueConfidence.FIRM));
    }
}
