package com.mcpscanner.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.config.ClientIdentity;
import com.mcpscanner.config.ClientIdentityField;
import com.mcpscanner.config.ClientIdentityProvider;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ClientIdentityHandshakeTest {

    private static final ClientIdentity CONFIGURED = ClientIdentity.from(field -> Map.of(
            ClientIdentityField.NAME, "acme-scanner",
            ClientIdentityField.TITLE, "Acme Scanner",
            ClientIdentityField.WEBSITE_URL, "https://acme.example",
            ClientIdentityField.ICON_URL, "https://acme.example/icon.png").get(field));

    private McpFixtureServer server;
    private McpClientManager manager;

    @AfterEach
    void stop() {
        manager.disconnect();
        server.close();
    }

    @Test
    void initializeHandshakeCarriesConfiguredClientInfo() throws IOException {
        connect(McpFixtureProfile.streamable("2025-06-18"), TransportType.STREAMABLE_HTTP, CONFIGURED);

        assertConfigured(server.callsTo("initialize").get(0).params().path("clientInfo"));
    }

    @Test
    void modernRequestMetadataCarriesConfiguredClientInfo() throws IOException {
        connect(McpFixtureProfile.modernOnly(), TransportType.MODERN_HTTP, CONFIGURED);

        JsonNode meta = server.callsTo("tools/list").get(0).params().path("_meta");
        assertConfigured(meta.path("io.modelcontextprotocol/clientInfo"));
    }

    @Test
    void sseHandshakeCarriesConfiguredClientInfo() throws IOException {
        connect(McpFixtureProfile.sseOnly("2024-11-05"), TransportType.SSE, CONFIGURED);

        assertConfigured(server.callsTo("initialize").get(0).params().path("clientInfo"));
    }

    @Test
    void defaultsKeepTheOriginalNameVersionOnlyClientInfo() throws IOException {
        connect(McpFixtureProfile.streamable("2025-06-18"), TransportType.STREAMABLE_HTTP, ClientIdentity.defaults());

        JsonNode info = server.callsTo("initialize").get(0).params().path("clientInfo");
        assertThat(info.fieldNames()).toIterable().containsExactly("name", "version");
        assertThat(server.callsTo("initialize").get(0).params().path("capabilities").size()).isZero();
    }

    private void assertConfigured(JsonNode info) {
        assertThat(info.path("name").asText()).isEqualTo("acme-scanner");
        assertThat(info.path("title").asText()).isEqualTo("Acme Scanner");
        assertThat(info.path("websiteUrl").asText()).isEqualTo("https://acme.example");
        assertThat(info.path("icons").get(0).path("src").asText()).isEqualTo("https://acme.example/icon.png");
    }

    private void connect(McpFixtureProfile profile, TransportType transport, ClientIdentity identity)
            throws IOException {
        server = new McpFixtureServer(profile);
        manager = new McpClientManager(McpEventLog.noop(), () -> identity,
                new JdkProtocolHttpTransport(McpHttpClients.upstream()));
        manager.connect(new McpServerConfig(server.endpoint(), transport, new NoAuthStrategy()));
    }
}
