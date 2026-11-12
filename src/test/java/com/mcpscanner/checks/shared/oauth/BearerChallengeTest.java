package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BearerChallengeTest {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void readsResourceMetadataAndScopeFromABearerChallenge() {
        BearerChallenge challenge = BearerChallenge.from(List.of(
                "Bearer realm=\"mcp\", scope=\" files:read \", resource_metadata=\"https://mcp.example/prm\""));

        assertThat(challenge.resourceMetadata()).contains(URI.create("https://mcp.example/prm"));
        assertThat(challenge.scope()).contains("files:read");
    }

    @Test
    void matchesTheResourceMetadataParameterCaseInsensitively() {
        assertThat(BearerChallenge.from(List.of("Bearer RESOURCE_METADATA = \"https://meta.example/prm\""))
                .resourceMetadata()).contains(URI.create("https://meta.example/prm"));
    }

    @Test
    void ignoresNonBearerSchemesAndUnsafeMetadataUrls() {
        BearerChallenge challenge = BearerChallenge.from(List.of(
                "Basic resource_metadata=\"https://mcp.example/basic\"",
                "Bearer resource_metadata=\"javascript:alert(1)\", scope=\"\""));

        assertThat(challenge.resourceMetadata()).isEmpty();
        assertThat(challenge.scope()).isEmpty();
    }

    @Test
    void noChallengeHasNothing() {
        assertThat(BearerChallenge.from(List.of())).isEqualTo(BearerChallenge.none());
    }

    @Test
    void readsEveryWwwAuthenticateHeaderOfAResponse() {
        HttpResponse response = HttpResponse.httpResponse("HTTP/1.1 401 Unauthorized\r\n"
                + "WWW-Authenticate: Basic realm=\"x\"\r\n"
                + "www-authenticate: Bearer resource_metadata=\"https://mcp.example/prm\"\r\n\r\n");

        assertThat(BearerChallenge.of(response).resourceMetadata()).contains(URI.create("https://mcp.example/prm"));
    }

    @Test
    void aMissingResponseHasNoChallenge() {
        assertThat(BearerChallenge.of(null)).isEqualTo(BearerChallenge.none());
    }
}
