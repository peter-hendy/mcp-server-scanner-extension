package com.mcpscanner.checks.shared.oauth;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class WellKnownLocationsTest {

    @Test
    void protectedResourceMetadataTriesThePathSpecificThenTheOriginDocument() {
        assertThat(WellKnownLocations.protectedResourceMetadata(URI.create("https://mcp.example:8443/tenant/mcp?x=1")))
                .containsExactly(
                        URI.create("https://mcp.example:8443/.well-known/oauth-protected-resource/tenant/mcp"),
                        URI.create("https://mcp.example:8443/.well-known/oauth-protected-resource"));
    }

    @Test
    void protectedResourceMetadataDropsATerminatingSlash() {
        assertThat(WellKnownLocations.protectedResourceMetadata(URI.create("https://mcp.example/mcp/")))
                .containsExactly(
                        URI.create("https://mcp.example/.well-known/oauth-protected-resource/mcp"),
                        URI.create("https://mcp.example/.well-known/oauth-protected-resource"));
    }

    @Test
    void rootResourceHasASingleProtectedResourceLocation() {
        assertThat(WellKnownLocations.protectedResourceMetadata(URI.create("https://mcp.example/")))
                .containsExactly(URI.create("https://mcp.example/.well-known/oauth-protected-resource"));
    }

    @Test
    void authorizationServerMetadataCoversRfc8414AndOpenIdPlacementsThenTheOriginDocument() {
        assertThat(WellKnownLocations.authorizationServerMetadata(URI.create("https://as.example/api/auth/")))
                .containsExactly(
                        URI.create("https://as.example/.well-known/oauth-authorization-server/api/auth"),
                        URI.create("https://as.example/api/auth/.well-known/oauth-authorization-server"),
                        URI.create("https://as.example/.well-known/openid-configuration/api/auth"),
                        URI.create("https://as.example/api/auth/.well-known/openid-configuration"),
                        URI.create("https://as.example/.well-known/oauth-authorization-server"));
    }

    @Test
    void rootIssuerHasOneLocationPerDocument() {
        assertThat(WellKnownLocations.authorizationServerMetadata(URI.create("http://127.0.0.1:8080")))
                .containsExactly(URI.create("http://127.0.0.1:8080/.well-known/oauth-authorization-server"),
                        URI.create("http://127.0.0.1:8080/.well-known/openid-configuration"));
        assertThat(WellKnownLocations.authorizationServerMetadata(URI.create("https://as.example/")))
                .containsExactly(URI.create("https://as.example/.well-known/oauth-authorization-server"),
                        URI.create("https://as.example/.well-known/openid-configuration"));
    }

    @Test
    void anIssuerWhoseHostJavaCannotParseKeepsItsRawAuthority() {
        assertThat(WellKnownLocations.authorizationServerMetadata(URI.create("http://auth_server:9000/realm")))
                .startsWith(URI.create("http://auth_server:9000/.well-known/oauth-authorization-server/realm"))
                .contains(URI.create("http://auth_server:9000/.well-known/openid-configuration/realm"));
    }

    @Test
    void aNonWebIssuerHasNoLocations() {
        assertThat(WellKnownLocations.authorizationServerMetadata(URI.create("urn:example:as"))).isEmpty();
        assertThat(WellKnownLocations.protectedResourceMetadata(URI.create("ftp://as.example/x"))).isEmpty();
    }
}
