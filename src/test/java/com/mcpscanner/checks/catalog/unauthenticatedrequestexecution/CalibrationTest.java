package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.issue.IssueRating;
import com.mcpscanner.checks.shared.auth.CatalogAccess;
import com.mcpscanner.checks.shared.probe.McpMethodClass;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CalibrationTest {

    @ParameterizedTest
    @CsvSource({
            "ANONYMOUS_EXECUTION,OPERATION,true,LOW,TENTATIVE",
            "ANONYMOUS_EXECUTION,OPERATION,false,LOW,TENTATIVE",
            "ANONYMOUS_EXECUTION_DESPITE_ENFORCEMENT,OPERATION,true,MEDIUM,FIRM",
            "ANONYMOUS_CATALOG,CATALOG,true,INFORMATION,FIRM",
            "ANONYMOUS_CATALOG,CATALOG,false,INFORMATION,FIRM",
            "AUTHENTICATION_BYPASS,OPERATION,true,HIGH,FIRM",
            "AUTHENTICATION_BYPASS,CATALOG,true,LOW,FIRM",
            "AUTHENTICATION_BYPASS,PROTOCOL,true,LOW,TENTATIVE",
            "AUTHENTICATION_BYPASS,NOTIFICATION,true,INFORMATION,TENTATIVE",
            "SESSION_CREDENTIAL,OPERATION,true,LOW,FIRM",
            "SESSION_CREDENTIAL,CATALOG,true,LOW,FIRM",
            "SESSION_CREDENTIAL,PROTOCOL,true,LOW,TENTATIVE",
            "SESSION_CREDENTIAL,NOTIFICATION,true,LOW,TENTATIVE",
            "INVALID_CREDENTIAL_ACCEPTED,OPERATION,true,MEDIUM,FIRM",
            "INVALID_CREDENTIAL_ACCEPTED,CATALOG,true,INFORMATION,FIRM",
            "FORWARDED_CLIENT_ADDRESS_TRUSTED,OPERATION,true,MEDIUM,FIRM",
            "FORWARDED_CLIENT_ADDRESS_TRUSTED,CATALOG,true,LOW,FIRM"
    })
    void ratesEachFindingFromWhatItsEvidenceProves(FindingKind kind, McpMethodClass methodClass, boolean credentials,
                                                   AuditIssueSeverity severity, AuditIssueConfidence confidence) {
        AuditSubject subject = new AuditSubject(mock(HttpRequestResponse.class),
                new SeedMessage("m", methodClass), credentials);

        assertThat(new Calibration().rate(new Finding(kind, subject, "v", "", List.of())))
                .isEqualTo(new IssueRating(severity, confidence));
    }

    @ParameterizedTest
    @CsvSource({"AUTHENTICATION_BYPASS,ALTERNATE_ROUTE", "SESSION_CREDENTIAL,SESSION_CREDENTIAL"})
    void ratesCatalogOnlyEvidenceFromTheSharedCatalogAccessRating(FindingKind kind, CatalogAccess access) {
        AuditSubject subject = new AuditSubject(mock(HttpRequestResponse.class),
                new SeedMessage("m", McpMethodClass.CATALOG), true);

        assertThat(new Calibration().rate(new Finding(kind, subject, "v", "", List.of()))).isSameAs(access.rating());
    }
}
