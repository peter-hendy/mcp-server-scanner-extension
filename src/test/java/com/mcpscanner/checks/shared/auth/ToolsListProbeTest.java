package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolsListProbeTest {

    private final ToolsListProbe probe = new ToolsListProbe(40);

    @Test
    void replacesTheBodyWithAToolsListRequestAndKeepsTheHeaders() throws Exception {
        HttpRequest probed = probe.on(Seeds.authenticated("http://127.0.0.1:8000/mcp"));

        JsonNode body = McpObjectMapper.INSTANCE.readTree(probed.bodyToString());
        assertThat(body.path("method").asText()).isEqualTo("tools/list");
        assertThat(body.path("id").asInt()).isEqualTo(40);
        assertThat(probed.headerValue("Mcp-Session-Id")).isEqualTo(Seeds.LIVE_SESSION);
    }

    @Test
    void everyProbeCarriesTheNextId() throws Exception {
        probe.on(Seeds.authenticated("http://127.0.0.1:8000/mcp"));

        String second = probe.on(Seeds.authenticated("http://127.0.0.1:8000/mcp")).bodyToString();
        assertThat(McpObjectMapper.INSTANCE.readTree(second).path("id").asInt()).isEqualTo(41);
    }
}
