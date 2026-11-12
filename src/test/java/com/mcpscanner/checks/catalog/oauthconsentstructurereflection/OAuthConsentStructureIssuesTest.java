package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.shared.oauth.RegisteredClient;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthConsentStructureIssuesTest {

    private static final StructureMarker MARKER = StructureMarker.withNonce("abcd");

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void attributeIssueNamesTheAttributeMarkerAndCarriesAllEvidence() {
        HttpRequestResponse registration = exchange("https://as.example/register");
        HttpRequestResponse redirect = exchange("https://as.example/authorize");
        HttpRequestResponse consent = exchange("https://as.example/consent");
        HttpRequestResponse control = exchange("https://as.example/authorize?control");
        ConsentStructureEvidence evidence = new ConsentStructureEvidence(
                new RegisteredClient("cid", List.of(ConsentClientRegistration.REDIRECT_URI.toString()), registration,
                        Optional.empty()),
                new AuthorizationPage(URI.create("https://as.example/consent"), List.of(redirect, consent)),
                new AuthorizationPage(URI.create("https://as.example/authorize?control"), List.of(control)),
                List.of(new FieldReflection(ReflectedField.STATE, MARKER, StructurePlacement.ATTRIBUTE)),
                ScriptExecutionAssessment.of(List.of()));

        List<AuditIssue> issues = new OAuthConsentStructureIssues(OAuthConsentStructureDescriptor.DESCRIPTOR)
                .issuesFor(evidence);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.detail()).contains("mcpscan-at-abcd").doesNotContain("(mcpscan-el-abcd)")
                    .contains("cid.mcpscan-unregistered-control")
                    .contains("No enforced Content-Security-Policy");
            assertThat(issue.remediation()).contains("Encode client metadata");
            assertThat(issue.requestResponses()).containsExactly(registration, redirect, consent, control);
        });
    }

    private static HttpRequestResponse exchange(String url) {
        return HttpRequestResponse.httpRequestResponse(HttpRequest.httpRequestFromUrl(url),
                HttpResponse.httpResponse("HTTP/1.1 200 OK\r\n\r\n"));
    }
}
