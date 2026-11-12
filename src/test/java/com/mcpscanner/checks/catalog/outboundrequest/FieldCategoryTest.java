package com.mcpscanner.checks.catalog.outboundrequest;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.issue.IssueRating;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FieldCategoryTest {

    @Test
    void ratesARequestFieldByWhatTheInteractionProves() {
        assertThat(FieldCategory.REQUEST_FIELD.rating(InteractionStrength.HTTP_REQUEST, false))
                .isEqualTo(new IssueRating(AuditIssueSeverity.MEDIUM, AuditIssueConfidence.FIRM));
        assertThat(FieldCategory.REQUEST_FIELD.rating(InteractionStrength.DNS_LOOKUP_ONLY, false))
                .isEqualTo(new IssueRating(AuditIssueSeverity.LOW, AuditIssueConfidence.TENTATIVE));
    }

    @Test
    void ratesMetadataFetchesAsInformation() {
        for (FieldCategory category : new FieldCategory[] {FieldCategory.CLIENT_METADATA,
                FieldCategory.OAUTH_CLIENT_METADATA}) {
            assertThat(category.rating(InteractionStrength.HTTP_REQUEST, false))
                    .isEqualTo(new IssueRating(AuditIssueSeverity.INFORMATION, AuditIssueConfidence.FIRM));
            assertThat(category.rating(InteractionStrength.DNS_LOOKUP_ONLY, false))
                    .isEqualTo(new IssueRating(AuditIssueSeverity.INFORMATION, AuditIssueConfidence.TENTATIVE));
        }
    }

    @Test
    void ratesFetchingByDesignAsInformationUntilCredentialsAreForwarded() {
        assertThat(FieldCategory.FETCH_BY_DESIGN.rating(InteractionStrength.HTTP_REQUEST, false))
                .isEqualTo(new IssueRating(AuditIssueSeverity.INFORMATION, AuditIssueConfidence.FIRM));
        assertThat(FieldCategory.FETCH_BY_DESIGN.reported(false)).isEqualTo(FieldCategory.FETCH_BY_DESIGN);
        assertThat(FieldCategory.FETCH_BY_DESIGN.reported(true)).isEqualTo(FieldCategory.REQUEST_FIELD);
        assertThat(FieldCategory.REQUEST_FIELD.reported(true)).isEqualTo(FieldCategory.REQUEST_FIELD);
    }

    @Test
    void ratesAnyForwardedCredentialHigh() {
        Arrays.stream(FieldCategory.values()).forEach(category ->
                assertThat(category.rating(InteractionStrength.HTTP_REQUEST, true))
                        .isEqualTo(new IssueRating(AuditIssueSeverity.HIGH, AuditIssueConfidence.FIRM)));
    }

    @Test
    void neverClaimsServerSideRequestForgery() {
        Arrays.stream(FieldCategory.values()).forEach(category -> {
            String text = (category.issueName() + category.scope() + category.remediation()).toLowerCase(Locale.ROOT);
            assertThat(text).doesNotContain("ssrf").doesNotContain("server-side request forgery");
        });
    }
}
