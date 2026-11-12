package com.mcpscanner.mcp;

import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InFlightServerPingsTest {
    private static final McpRequests.Request CALL = new McpRequests.Request(
            "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/list\",\"params\":{}}",
            Map.of("Authorization", "Bearer call", "MCP-Protocol-Version", "2024-11-05"));
    private static final McpRequests.Request NEXT_CALL = new McpRequests.Request(
            "{\"jsonrpc\":\"2.0\",\"id\":\"six\",\"method\":\"tools/list\",\"params\":{}}", Map.of());
    private static final McpRequests.Request NOTIFICATION = new McpRequests.Request(
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", Map.of());
    private static final String CALL_REPLY = "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{}}";

    private final List<McpRequests.Request> posted = new ArrayList<>();
    private final InFlightServerPings pings = new InFlightServerPings(new ServerPings(() -> true), posted::add);

    @Test
    void ignoresPingsWhileNoCallIsPending() {
        assertThat(pings.consume(ping(1))).isTrue();
        pings.callStarted(NOTIFICATION);
        assertThat(pings.consume(ping(2))).isTrue();

        assertThat(posted).isEmpty();
    }

    @Test
    void answersAtMostTheBudgetDuringOneCallWithThatCallsHeaders() {
        pings.callStarted(CALL);

        sendPings(ServerPingAnswerer.MAX_REPLIES_PER_CALL + 4);

        assertThat(posted).hasSize(ServerPingAnswerer.MAX_REPLIES_PER_CALL)
                .allSatisfy(reply -> assertThat(reply.headers()).containsEntry("Authorization", "Bearer call"));
    }

    @Test
    void stopsAnsweringOnceTheCallsReplyArrives() {
        pings.callStarted(CALL);
        assertThat(pings.consume(CALL_REPLY)).isFalse();

        sendPings(3);

        assertThat(posted).isEmpty();
    }

    @Test
    void stopsAnsweringOnceTheCallEnds() {
        pings.callStarted(CALL);
        pings.callEnded(CALL);

        sendPings(3);

        assertThat(posted).isEmpty();
    }

    @Test
    void consecutiveCallsEachGetTheirOwnBudget() {
        pings.callStarted(CALL);
        sendPings(ServerPingAnswerer.MAX_REPLIES_PER_CALL + 4);
        assertThat(pings.consume(CALL_REPLY)).isFalse();
        pings.callStarted(NEXT_CALL);
        sendPings(ServerPingAnswerer.MAX_REPLIES_PER_CALL + 4);

        assertThat(posted).hasSize(2 * ServerPingAnswerer.MAX_REPLIES_PER_CALL);
    }

    @Test
    void leavesRepliesAndNotificationsToTheCaller() {
        pings.callStarted(CALL);

        assertThat(pings.consume(CALL_REPLY)).isFalse();
        assertThat(pings.consume("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}")).isFalse();
    }

    private void sendPings(int count) {
        for (int ping = 0; ping < count; ping++) {
            assertThat(pings.consume(ping(ping))).isTrue();
        }
    }

    private static String ping(int id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":\"p" + id + "\",\"method\":\"ping\"}";
    }
}
