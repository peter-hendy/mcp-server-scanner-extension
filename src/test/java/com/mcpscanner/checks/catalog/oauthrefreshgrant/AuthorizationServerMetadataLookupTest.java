package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import com.mcpscanner.checks.shared.oauth.JsonDocument;
import com.mcpscanner.checks.shared.oauth.OAuthHttp;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationServerMetadataLookupTest {

    private static final URI ISSUER = URI.create("https://as.example");

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    private static Optional<AuthorizationServerMetadata> read(String body) {
        return JsonDocument.parse(body).flatMap(document -> AuthorizationServerMetadataLookup.read(document, ISSUER));
    }

    @Test
    void parsesTheTokenEndpointScopesAndAuthMethods() {
        AuthorizationServerMetadata metadata = read(
                "{\"issuer\":\"https://as.example\",\"token_endpoint\":\"https://as.example/token\",\"scopes_supported\":[\"a\",1],"
                        + "\"token_endpoint_auth_methods_supported\":[\"client_secret_basic\"]}").orElseThrow();

        assertThat(metadata.tokenEndpoint()).isEqualTo(URI.create("https://as.example/token"));
        assertThat(metadata.scopesSupported()).containsExactly("a");
        assertThat(metadata.admitsOnlyConfidentialClients()).isTrue();
    }

    @Test
    void serversThatAdmitPublicClientsOrDoNotSayAreNotConfidentialOnly() {
        assertThat(read("{\"issuer\":\"https://as.example/\",\"token_endpoint\":\"https://as.example/t\","
                + "\"token_endpoint_auth_methods_supported\":[\"none\",\"client_secret_basic\"]}")
                .orElseThrow().admitsOnlyConfidentialClients()).isFalse();
        assertThat(read("{\"issuer\":\"https://as.example\",\"token_endpoint\":\"https://as.example/t\"}")
                .orElseThrow().admitsOnlyConfidentialClients()).isFalse();
    }

    @Test
    void rejectsDocumentsWithoutATokenEndpoint() {
        assertThat(read("{\"issuer\":\"https://as.example\"}")).isEmpty();
        assertThat(read("<html>")).isEmpty();
        assertThat(read("{\"issuer\":\"https://as.example\",\"token_endpoint\":\"not a uri\"}")).isEmpty();
    }

    @Test
    void rejectsMetadataDeclaringAnotherIssuer() {
        assertThat(read("{\"issuer\":\"https://evil.example\",\"token_endpoint\":\"https://evil.example/t\"}")).isEmpty();
        assertThat(read("{\"token_endpoint\":\"https://as.example/t\"}")).isEmpty();
    }

    @Test
    void findsNothingWhenNoWellKnownDocumentIsServed() {
        ScriptedOAuthMcpServer server = new ScriptedOAuthMcpServer();
        server.servesMetadata = false;

        assertThat(new AuthorizationServerMetadataLookup(new OAuthHttp(server.http())).lookup(URI.create(ScriptedOAuthMcpServer.ORIGIN)))
                .isEmpty();
    }

    @Test
    void findsTheMetadataServedForTheIssuer() {
        ScriptedOAuthMcpServer server = new ScriptedOAuthMcpServer();

        assertThat(new AuthorizationServerMetadataLookup(new OAuthHttp(server.http())).lookup(URI.create(ScriptedOAuthMcpServer.ORIGIN)))
                .isPresent();
    }
}
