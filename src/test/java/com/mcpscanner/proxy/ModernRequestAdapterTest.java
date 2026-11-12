package com.mcpscanner.proxy;

import com.mcpscanner.testutil.PlainFraming;
import com.mcpscanner.mcp.McpOperationFraming;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ParamHeaderFramer;
import com.mcpscanner.mcp.ToolParamHeaders;
import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModernRequestAdapterTest {

    private final McpOperationFraming.Framer framer = PlainFraming.withOperations(
            (method, params, id) -> McpRequests.modernOperation(method, "burp", "1", params, id));

    @Test
    void addsModernMetadataToLegacyScanRequest() {
        String original = "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"echo\",\"arguments\":{\"text\":\"fuzzed\"}}}";

        McpRequests.Request adapted = ModernRequestAdapter.adapt(original, framer);

        assertThat(adapted.body()).contains("\"_meta\"").contains("\"text\":\"fuzzed\"");
        assertThat(adapted.headers()).containsEntry("Mcp-Method", "tools/call")
                .containsEntry("Mcp-Name", "echo")
                .containsEntry("MCP-Protocol-Version", "2026-07-28");
    }

    @Test
    void addsModernMetadataBesideAClientProgressToken() {
        String original = "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\","
                + "\"params\":{\"_meta\":{\"progressToken\":\"scan\"},\"name\":\"echo\",\"arguments\":{}}}";

        McpRequests.Request adapted = ModernRequestAdapter.adapt(original, framer);

        assertThat(adapted.body()).contains("\"progressToken\":\"scan\"")
                .contains("\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"");
        assertThat(adapted.headers()).containsEntry("Mcp-Name", "echo");
    }

    @Test
    void preservesModernBodyAndUpdatesNameHeaderAfterFuzzing() {
        String original = McpRequests.modernOperation("resources/read", "burp", "1",
                "{\"uri\":\"file:///safe\"}", 2).body();
        String mutated = original.replace("file:///safe", "file:///changed");

        McpRequests.Request adapted = ModernRequestAdapter.adapt(mutated, framer);

        assertThat(adapted.body()).isEqualTo(mutated);
        assertThat(adapted.headers()).containsEntry("Mcp-Name", "file:///changed");
    }

    @Test
    void derivesParamHeadersFromAFuzzedToolArgument() {
        String original = McpRequests.modernOperation("tools/call", "burp", "1",
                "{\"name\":\"lookup\",\"arguments\":{\"tenant\":\"acme\"}}", 3).body();
        String fuzzed = original.replace("\"acme\"", "\"acme' OR 1=1\"");

        McpRequests.Request adapted = ModernRequestAdapter.adapt(fuzzed, paramHeaderFramer());

        assertThat(adapted.body()).isEqualTo(fuzzed);
        assertThat(adapted.headers()).containsEntry("Mcp-Param-Tenant", "acme' OR 1=1");
    }

    @Test
    void derivesParamHeadersForAnUnframedScanRequest() {
        String original = "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"lookup\",\"arguments\":{\"tenant\":\"\"}}}";

        McpRequests.Request adapted = ModernRequestAdapter.adapt(original, paramHeaderFramer());

        assertThat(adapted.headers()).containsEntry("Mcp-Param-Tenant", "=?base64??=");
    }

    private McpOperationFraming.Framer paramHeaderFramer() {
        ToolParamHeaders tools = new ToolParamHeaders(warning -> {});
        tools.learn(List.of(new McpToolDefinition("lookup", "", "{\"type\":\"object\",\"properties\":"
                + "{\"tenant\":{\"type\":\"string\",\"x-mcp-header\":\"Tenant\"}}}")));
        return new ParamHeaderFramer(framer, tools);
    }

    @Test
    void preservesMalformedProbeBody() {
        String malformed = "{\"jsonrpc\":\"2.0\",broken";

        McpRequests.Request adapted = ModernRequestAdapter.adapt(malformed, framer);

        assertThat(adapted.body()).isEqualTo(malformed);
        assertThat(adapted.headers()).isEmpty();
    }
}
