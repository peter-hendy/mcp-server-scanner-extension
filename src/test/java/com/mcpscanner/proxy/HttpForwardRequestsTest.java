package com.mcpscanner.proxy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpForwardRequestsTest {

    @Test
    void replacesIncomingParamHeadersWithThoseDerivedFromTheBody() {
        Map<String, String> incoming = Map.of("mcp-param-tenant", "stale", "MCP-PARAM-REGION", "fuzzed",
                "X-Custom", "kept");

        Map<String, String> headers = HttpForwardRequests.withOperationHeaders(incoming,
                Map.of("Mcp-Method", "tools/call", "Mcp-Param-Tenant", "acme"));

        assertThat(headers).containsEntry("Mcp-Param-Tenant", "acme").containsEntry("X-Custom", "kept")
                .doesNotContainKeys("mcp-param-tenant", "MCP-PARAM-REGION");
    }

    @Test
    void dropsIncomingParamHeadersWhenTheBodyDeclaresNone() {
        Map<String, String> headers = HttpForwardRequests.withOperationHeaders(
                Map.of("Mcp-Param-Tenant", "stale"), Map.of("Mcp-Method", "prompts/get"));

        assertThat(headers).doesNotContainKey("Mcp-Param-Tenant");
    }
}
