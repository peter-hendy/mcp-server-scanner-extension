package com.mcpscanner.mcp;

import com.mcpscanner.testutil.PlainFraming;
import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParamHeaderFramerTest {

    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{"
            + "\"tenant\":{\"type\":\"string\",\"x-mcp-header\":\"Tenant\"},"
            + "\"priority\":{\"type\":\"integer\",\"x-mcp-header\":\"Priority\"}}}";
    private static final String CALL_PARAMS = "{\"name\":\"lookup\",\"arguments\":{\"tenant\":\"acme\",\"priority\":3}}";

    private final ToolParamHeaders tools = new ToolParamHeaders(warning -> {});
    private final McpOperationFraming.Framer modern = new ParamHeaderFramer(PlainFraming.withOperations(
            (method, params, id) -> McpRequests.modernOperation(method, "burp", "1", params, id)), tools);

    @Test
    void addsTheDeclaredParamHeadersToAModernToolCall() {
        tools.learn(List.of(new McpToolDefinition("lookup", "", SCHEMA)));

        McpRequests.Request framed = modern.operation("tools/call", CALL_PARAMS, 4);

        assertThat(framed.headers()).containsEntry("Mcp-Param-Tenant", "acme")
                .containsEntry("Mcp-Param-Priority", "3")
                .containsEntry("Mcp-Name", "lookup");
    }

    @Test
    void leavesOtherMethodsUntouched() {
        tools.learn(List.of(new McpToolDefinition("lookup", "", SCHEMA)));

        McpRequests.Request framed = modern.operation("prompts/get", CALL_PARAMS, 4);

        assertThat(framed.headers().keySet()).noneMatch(name -> name.startsWith("Mcp-Param-"));
    }

    @Test
    void leavesHandshakeFramingUntouched() {
        tools.learn(List.of(new McpToolDefinition("lookup", "", SCHEMA)));
        McpOperationFraming.Framer handshake = new ParamHeaderFramer(PlainFraming.framer(), tools);

        McpRequests.Request framed = handshake.operation("tools/call", CALL_PARAMS, 4);

        assertThat(framed.headers()).isEmpty();
    }

    @Test
    void leavesAToolCallWithoutArgumentsUntouched() {
        tools.learn(List.of(new McpToolDefinition("lookup", "", SCHEMA)));

        assertThat(modern.operation("tools/call", "{\"name\":\"lookup\"}", 4).headers().keySet())
                .noneMatch(name -> name.startsWith("Mcp-Param-"));
    }

    @Test
    void keepsTheFramedBody() {
        tools.learn(List.of(new McpToolDefinition("lookup", "", SCHEMA)));

        assertThat(modern.operation("tools/call", CALL_PARAMS, 4).body())
                .isEqualTo(McpRequests.modernOperation("tools/call", "burp", "1", CALL_PARAMS, 4).body());
    }

    @Test
    void framesNotificationsThroughTheDelegate() {
        assertThat(modern.notification("notifications/initialized", "{}").body())
                .contains("notifications/initialized");
    }
}
