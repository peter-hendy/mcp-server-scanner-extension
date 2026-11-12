package com.mcpscanner.checks.shared.oauth;

import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.json;
import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationServerMetadataFetcherTest {

    private static final String METADATA = "{\"issuer\":\"https://auth.example.com/tenant\","
            + "\"authorization_endpoint\":\"https://auth.example.com/authorize\"}";
    private static final URI ISSUER_WITH_PATH = URI.create("https://auth.example.com/tenant/");
    private static final String OAUTH_INSERTED = "https://auth.example.com/.well-known/oauth-authorization-server/tenant";
    private static final String OAUTH_APPENDED = "https://auth.example.com/tenant/.well-known/oauth-authorization-server";
    private static final String OIDC_INSERTED = "https://auth.example.com/.well-known/openid-configuration/tenant";
    private static final String OIDC_APPENDED = "https://auth.example.com/tenant/.well-known/openid-configuration";
    private static final String OAUTH_ROOT = "https://auth.example.com/.well-known/oauth-authorization-server";

    private ScriptedHttp http;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        http = new ScriptedHttp();
    }

    private Optional<URI> fetch(URI issuer) {
        return new AuthorizationServerMetadataFetcher(http.oauth())
                .fetch(issuer, document -> document.webUri("authorization_endpoint"));
    }

    @Test
    void triesEveryWellKnownLocationInOrderForAnIssuerWithAPath() {
        assertThat(fetch(ISSUER_WITH_PATH)).isEmpty();

        assertThat(http.sentUrls())
                .containsExactly(OAUTH_INSERTED, OAUTH_APPENDED, OIDC_INSERTED, OIDC_APPENDED, OAUTH_ROOT);
    }

    @Test
    void triesEachDistinctLocationOnceForAnIssuerAtTheRoot() {
        fetch(URI.create("https://auth.example.com"));

        assertThat(http.sentUrls()).containsExactly(
                "https://auth.example.com/.well-known/oauth-authorization-server",
                "https://auth.example.com/.well-known/openid-configuration");
    }

    @Test
    void stopsAtTheFirstDocumentTheReaderAccepts() {
        http.on("GET", OAUTH_INSERTED, json(200, METADATA));

        assertThat(fetch(ISSUER_WITH_PATH)).contains(URI.create("https://auth.example.com/authorize"));
        assertThat(http.sentUrls()).containsExactly(OAUTH_INSERTED);
    }

    @Test
    void skipsAFailedResponse() {
        http.on("GET", OAUTH_INSERTED, json(500, METADATA)).on("GET", OIDC_APPENDED, json(200, METADATA));

        assertThat(fetch(ISSUER_WITH_PATH)).isPresent();
        assertThat(http.sentUrls()).containsExactly(OAUTH_INSERTED, OAUTH_APPENDED, OIDC_INSERTED, OIDC_APPENDED);
    }

    @Test
    void skipsADocumentTheReaderRejects() {
        http.on("GET", OAUTH_INSERTED, json(200, "{\"authorization_endpoint\":\"javascript:alert(1)\"}"))
                .on("GET", OAUTH_APPENDED, json(200, METADATA));

        assertThat(fetch(ISSUER_WITH_PATH)).isPresent();
        assertThat(http.sentUrls()).containsExactly(OAUTH_INSERTED, OAUTH_APPENDED);
    }

    @Test
    void stopsAfterTheServerAnswersTooManyRequests() {
        http.on("GET", OAUTH_INSERTED, ScriptedHttp.status(429)).on("GET", OIDC_APPENDED, json(200, METADATA));

        assertThat(fetch(ISSUER_WITH_PATH)).isEmpty();
        assertThat(http.sentUrls()).containsExactly(OAUTH_INSERTED);
    }
}
