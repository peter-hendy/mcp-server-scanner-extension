package com.mcpscanner.auth.oauth.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataParsersTest {

    @Test
    void ignoresScopeTextInsideOtherChallengeParameters() {
        assertThat(MetadataParsers.parseScopesFromWwwAuthenticate(
                "Bearer resource_metadata=\"https://example.com/prm?x=1,scope=admin\", realm=\"scope=admin\""))
                .isEmpty();
        assertThat(MetadataParsers.parseScopesFromWwwAuthenticate(
                "Basic realm=\"other\", scope=\"admin\", Bearer resource_metadata=\"https://example.com/prm\", scope=\"files:read\""))
                .contains(java.util.List.of("files:read"));
    }

    @Test
    void resourceBindingToleratesSchemeAndHostCaseButPreservesEscapesAndPort() {
        URI expected = URI.create("https://mcp.example.com/mcp%2Fdata");
        assertThat(MetadataParsers.matchesProtectedResource(
                MetadataParsers.parseJsonObject("{\"resource\":\"HTTPS://MCP.EXAMPLE.COM/mcp%2Fdata\"}").orElseThrow(),
                expected)).isTrue();
        assertThat(MetadataParsers.matchesProtectedResource(
                MetadataParsers.parseJsonObject("{\"resource\":\"https://mcp.example.com/mcp%2fdata\"}").orElseThrow(),
                expected)).isFalse();
        assertThat(MetadataParsers.matchesProtectedResource(
                MetadataParsers.parseJsonObject("{\"resource\":\"https://mcp.example.com:443/mcp%2Fdata\"}").orElseThrow(),
                expected)).isFalse();
    }

    @Test
    void parseResourceMetadataExtractsQuotedUrl() {
        Optional<URI> uri = MetadataParsers.parseResourceMetadataFromWwwAuthenticate(
                "Bearer realm=\"x\", resource_metadata=\"https://example.com/prm\"");

        assertThat(uri).contains(URI.create("https://example.com/prm"));
    }

    @Test
    void parseResourceMetadataReturnsEmptyWhenParameterMissing() {
        assertThat(MetadataParsers.parseResourceMetadataFromWwwAuthenticate("Bearer realm=\"x\""))
                .isEmpty();
    }

    @Test
    void parseResourceMetadataReturnsEmptyForNullHeader() {
        assertThat(MetadataParsers.parseResourceMetadataFromWwwAuthenticate(null)).isEmpty();
    }

    @Test
    void parseJsonObjectReturnsJsonNodeForValidObject() {
        Optional<JsonNode> node = MetadataParsers.parseJsonObject(
                "{\"resource\":\"https://example.com\",\"authorization_servers\":[\"https://auth.example.com\"]}");

        assertThat(node).isPresent();
        assertThat(node.get().get("resource").asText()).isEqualTo("https://example.com");
    }

    @Test
    void parseJsonObjectReturnsEmptyForMalformedJson() {
        assertThat(MetadataParsers.parseJsonObject("{not json")).isEmpty();
    }

    @Test
    void parseJsonObjectReturnsEmptyForJsonArrayRoot() {
        assertThat(MetadataParsers.parseJsonObject("[\"https://example.com\"]")).isEmpty();
    }

    @Test
    void parseJsonObjectReturnsEmptyForBlankInput() {
        assertThat(MetadataParsers.parseJsonObject("")).isEmpty();
        assertThat(MetadataParsers.parseJsonObject(null)).isEmpty();
    }

    @Test
    void parseAsReturnsMetadataForValidDocument() {
        String body = "{"
                + "\"issuer\":\"https://auth.example.com\","
                + "\"authorization_endpoint\":\"https://auth.example.com/authorize\","
                + "\"token_endpoint\":\"https://auth.example.com/token\","
                + "\"response_types_supported\":[\"code\"]"
                + "}";

        Optional<AuthorizationServerMetadata> metadata = MetadataParsers.parseAs(body);

        assertThat(metadata).isPresent();
        assertThat(metadata.get().getIssuer().getValue()).isEqualTo("https://auth.example.com");
    }

    @Test
    void parseAsReturnsEmptyForMalformedJson() {
        assertThat(MetadataParsers.parseAs("{not valid")).isEmpty();
    }
}
