package com.mcpscanner.mcp;

import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServerPingAnswererTest {
    private static final Map<String, String> SENT = Map.of(
            "Authorization", "Bearer token",
            "X-Trace", "abc",
            "Mcp-Method", "tools/call",
            "Mcp-Name", "echo",
            "MCP-Protocol-Version", "2025-06-18",
            "Mcp-Session-Id", "sid-1",
            "Content-Type", "application/json");

    private final List<McpRequests.Request> posted = new ArrayList<>();

    @Test
    void answersPingWithEmptyResultCarryingSessionAndCredentialHeaders() {
        boolean consumed = new ServerPings(() -> true).answerer(SENT, posted::add)
                .consume("{\"jsonrpc\":\"2.0\",\"id\":\"p-1\",\"method\":\"ping\"}");

        assertThat(consumed).isTrue();
        assertThat(posted).singleElement().satisfies(reply -> {
            assertThat(reply.body()).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":\"p-1\",\"result\":{}}");
            assertThat(reply.headers()).containsEntry("Authorization", "Bearer token")
                    .containsEntry("X-Trace", "abc")
                    .containsEntry("MCP-Protocol-Version", "2025-06-18")
                    .containsEntry("MCP-Session-Id", "sid-1")
                    .containsEntry("Content-Type", "application/json");
            assertThat(reply.headers().keySet()).noneMatch(name ->
                    name.equalsIgnoreCase("Mcp-Method") || name.equalsIgnoreCase("Mcp-Name"));
        });
    }

    @Test
    void preservesNumericPingIdAndDefaultsToTheSseVersionWithoutAVersionHeader() {
        new ServerPings(() -> true).answerer(Map.of(), posted::add)
                .consume("{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"ping\"}");

        assertThat(posted).singleElement().satisfies(reply -> {
            assertThat(reply.body()).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":42,\"result\":{}}");
            assertThat(reply.headers()).containsEntry("MCP-Protocol-Version", McpRequests.SSE_VERSION)
                    .doesNotContainKey("MCP-Session-Id");
        });
    }

    @Test
    void stopsAnsweringAfterTheReplyCapForOneCall() {
        var answerer = new ServerPings(() -> true).answerer(SENT, posted::add);

        for (int ping = 0; ping < ServerPingAnswerer.MAX_REPLIES_PER_CALL + 4; ping++) {
            assertThat(answerer.consume("{\"jsonrpc\":\"2.0\",\"id\":" + ping + ",\"method\":\"ping\"}")).isTrue();
        }

        assertThat(posted).hasSize(ServerPingAnswerer.MAX_REPLIES_PER_CALL);
    }

    @Test
    void consumesOtherServerRequestsWithoutAnswering() {
        assertThat(new ServerPings(() -> true).answerer(SENT, posted::add)
                .consume("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"sampling/createMessage\",\"params\":{}}")).isTrue();
        assertThat(posted).isEmpty();
    }

    @Test
    void leavesRepliesNotificationsAndMalformedDataToTheCaller() {
        var answerer = new ServerPings(() -> true).answerer(SENT, posted::add);

        assertThat(answerer.consume("{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{}}")).isFalse();
        assertThat(answerer.consume("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}")).isFalse();
        assertThat(answerer.consume("not json")).isFalse();
        assertThat(posted).isEmpty();
    }

    @Test
    void disabledSettingConsumesPingsWithoutAnswering() {
        assertThat(new ServerPings(() -> false).answerer(SENT, posted::add)
                .consume("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")).isTrue();
        assertThat(posted).isEmpty();
    }
}
