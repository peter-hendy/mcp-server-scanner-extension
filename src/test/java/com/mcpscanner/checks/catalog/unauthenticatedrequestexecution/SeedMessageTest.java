package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.probe.McpMethodClass;
import com.mcpscanner.checks.shared.probe.SimulatedMcpServer;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeedMessageTest {

    private static HttpRequest post(String body) {
        return ImmutableHttpRequest.post("http://mcp.example/mcp", Map.of(), body);
    }

    @Test
    void readsTheMethodAndClassOfARequest() {
        assertThat(SeedMessage.of(post(SimulatedMcpServer.toolCall())))
                .contains(new SeedMessage("tools/call", McpMethodClass.OPERATION));
    }

    @Test
    void messagesWithoutAnIdOrWithANullIdAreNotifications() {
        assertThat(SeedMessage.of(post(SimulatedMcpServer.notification())).orElseThrow().methodClass())
                .isEqualTo(McpMethodClass.NOTIFICATION);
        assertThat(SeedMessage.of(post("{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}")).orElseThrow()
                .methodClass()).isEqualTo(McpMethodClass.NOTIFICATION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not json", "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}]",
            "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"ping\"}", "{\"jsonrpc\":\"2.0\",\"id\":1}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":7}", "\"string\""})
    void anythingButASingleJsonRpcMessageIsIgnored(String body) {
        assertThat(SeedMessage.of(post(body))).isEmpty();
    }

    @Test
    void nonPostRequestsAndNullAreIgnored() {
        HttpRequest get = mock(HttpRequest.class);
        when(get.method()).thenReturn("GET");

        assertThat(SeedMessage.of(get)).isEmpty();
        assertThat(SeedMessage.of(null)).isEmpty();
    }
}
