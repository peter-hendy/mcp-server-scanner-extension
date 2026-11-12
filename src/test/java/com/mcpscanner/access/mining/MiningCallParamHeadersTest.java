package com.mcpscanner.access.mining;

import com.fasterxml.jackson.databind.node.TextNode;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ParamHeaderFramer;
import com.mcpscanner.mcp.ToolAnnotations;
import com.mcpscanner.mcp.ToolParamHeaders;
import com.mcpscanner.scan.request.McpHttpRequestFactory;
import com.mcpscanner.testutil.PlainFraming;
import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MiningCallParamHeadersTest {

    private static final McpToolDefinition GET_INVOICE = new McpToolDefinition("get_invoice", "",
            "{\"type\":\"object\",\"properties\":{\"tenant\":{\"type\":\"string\",\"x-mcp-header\":\"Tenant\"},"
                    + "\"invoice_id\":{\"type\":\"string\"}},\"required\":[\"tenant\"]}",
            List.of(), new ToolAnnotations(null, true, null, null, null));

    @Test
    void aMiningToolCallIsFramedWithTheMcpParamHeadersItsToolDeclares() {
        ToolParamHeaders tools = new ToolParamHeaders(warning -> { });
        tools.learn(List.of(GET_INVOICE));
        McpHttpRequestFactory factory = new McpHttpRequestFactory(() -> 1L, new ParamHeaderFramer(
                PlainFraming.withOperations((method, params, id) ->
                        McpRequests.modernOperation(method, "burp", "1", params, id)), tools));

        String raw = factory.raw("http://127.0.0.1:8000/mcp",
                new MiningCall.ToolCall(GET_INVOICE, Map.of("tenant", TextNode.valueOf("acme-mined"))).message(),
                Map.of("mcp-protocol-version", "2026-07-28"));

        assertThat(raw).contains("\r\nMcp-Param-Tenant: acme-mined\r\n");
    }
}
