package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mcpscanner.checks.catalog.oauthconsentstructurereflection.ScriptedConsentServer.RAW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OAuthConsentStructureCheckTest {

    private final McpEventLog eventLog = mock(McpEventLog.class);
    private final OAuthConsentStructureCheck check = new OAuthConsentStructureCheck(eventLog);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void descriptorIsAnInformationalTentativeHostCheck() {
        assertThat(check.descriptor().id()).isEqualTo("oauth-consent-structure-reflection");
        assertThat(check.descriptor().kind()).isEqualTo(CheckKind.HOST);
        assertThat(check.descriptor().headlineSeverity()).isEqualTo(AuditIssueSeverity.INFORMATION);
        assertThat(check.descriptor().headlineConfidence()).isEqualTo(AuditIssueConfidence.TENTATIVE);
        assertThat(check.descriptor().cwes()).extracting(cwe -> cwe.id()).containsExactly(116);
    }

    @Test
    void isDiscoveredFromTheServiceFile() {
        assertThat(CheckCatalog.discover(CheckServices.standalone()).find("oauth-consent-structure-reflection"))
                .hasValueSatisfying(found -> assertThat(found).isInstanceOf(OAuthConsentStructureCheck.class));
    }

    @Test
    void appliesOnlyToMcpRequests() {
        assertThat(check.appliesTo(ScriptedConsentServer.mcpBase())).isTrue();
        assertThat(check.appliesTo(HttpRequestResponse.httpRequestResponse(
                HttpRequest.httpRequestFromUrl("https://mcp.example/"),
                HttpResponse.httpResponse("HTTP/1.1 200 OK\r\n\r\n")))).isFalse();
    }

    @Test
    void reportsOneInformationalTentativeIssuePerReflectedField() {
        List<AuditIssue> issues = audit(new ScriptedConsentServer().clientName(RAW).state(RAW));

        assertThat(issues).extracting(AuditIssue::name).containsExactly(
                "OAuth authorization flow page renders client_name as HTML structure",
                "OAuth authorization flow page renders state as HTML structure");
        assertThat(issues).allSatisfy(issue -> {
            assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.INFORMATION);
            assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.TENTATIVE);
            assertThat(issue.detail()).doesNotContainIgnoringCase("xss")
                    .contains("does not show that script runs");
        });
    }

    @Test
    void escapingServerRaisesNothingAndLogsWhy() {
        assertThat(audit(new ScriptedConsentServer())).isEmpty();

        verify(eventLog).info(contains("no marker parsed as HTML structure"));
    }

    @Test
    void cspThatBlocksScriptIsReportedButDoesNotChangeTheRating() {
        List<AuditIssue> issues = audit(new ScriptedConsentServer().clientName(RAW)
                .consentHeaders("Content-Security-Policy: default-src 'self'"));

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.INFORMATION);
            assertThat(issue.detail()).contains("allows no inline script");
        });
    }

    private List<AuditIssue> audit(ScriptedConsentServer server) {
        return check.audit(ScriptedConsentServer.mcpBase(), server.build().http());
    }
}
