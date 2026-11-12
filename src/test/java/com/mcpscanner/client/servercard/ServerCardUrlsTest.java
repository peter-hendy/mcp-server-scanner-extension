package com.mcpscanner.client.servercard;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ServerCardUrlsTest {

    @Test
    void appendsServerCardToEndpointPathAndUsesOriginForCatalog() {
        ServerCardUrls urls = ServerCardUrls.forEndpoint(URI.create("https://mcp.example/api/mcp"));

        assertThat(urls.serverCard()).isEqualTo(URI.create("https://mcp.example/api/mcp/server-card"));
        assertThat(urls.aiCatalog()).isEqualTo(URI.create("https://mcp.example/.well-known/ai-catalog.json"));
    }

    @Test
    void stripsTrailingSlashFromEndpointPath() {
        ServerCardUrls urls = ServerCardUrls.forEndpoint(URI.create("https://mcp.example/mcp/"));

        assertThat(urls.serverCard()).isEqualTo(URI.create("https://mcp.example/mcp/server-card"));
    }

    @Test
    void dropsQueryAndFragmentFromEndpoint() {
        ServerCardUrls urls = ServerCardUrls.forEndpoint(
                URI.create("https://mcp.example/mcp?token=secret#frag"));

        assertThat(urls.serverCard()).isEqualTo(URI.create("https://mcp.example/mcp/server-card"));
        assertThat(urls.aiCatalog()).isEqualTo(URI.create("https://mcp.example/.well-known/ai-catalog.json"));
    }

    @Test
    void rootEndpointWithAndWithoutSlashYieldsRootServerCard() {
        assertThat(ServerCardUrls.forEndpoint(URI.create("https://mcp.example/")).serverCard())
                .isEqualTo(URI.create("https://mcp.example/server-card"));
        assertThat(ServerCardUrls.forEndpoint(URI.create("https://mcp.example")).serverCard())
                .isEqualTo(URI.create("https://mcp.example/server-card"));
    }

    @Test
    void preservesHttpSchemeAndExplicitPort() {
        ServerCardUrls urls = ServerCardUrls.forEndpoint(URI.create("http://localhost:8000/mcp"));

        assertThat(urls.serverCard()).isEqualTo(URI.create("http://localhost:8000/mcp/server-card"));
        assertThat(urls.aiCatalog()).isEqualTo(URI.create("http://localhost:8000/.well-known/ai-catalog.json"));
    }

    @Test
    void dropsUserInfoFromBothUrls() {
        ServerCardUrls urls = ServerCardUrls.forEndpoint(URI.create("https://user:pw@mcp.example/mcp"));

        assertThat(urls.serverCard()).isEqualTo(URI.create("https://mcp.example/mcp/server-card"));
        assertThat(urls.aiCatalog()).isEqualTo(URI.create("https://mcp.example/.well-known/ai-catalog.json"));
    }
}
