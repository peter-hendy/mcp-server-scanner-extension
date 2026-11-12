package com.mcpscanner.checks.catalog.sessionlifecycle;

import com.mcpscanner.checks.shared.jsonrpc.JsonRpcEnvelope;
import com.mcpscanner.testutil.FakeMcpServer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PingRequestsTest {

    @Test
    void everyPingOfARunHasItsOwnIdAndRunsDoNotShareACounter() {
        PingRequests first = new PingRequests();
        long a = id(first.withSession(FakeMcpServer.LEGACY_VERSION, "s").body());
        long b = id(first.withoutSession(FakeMcpServer.LEGACY_VERSION).body());
        long c = id(first.liveBody());

        assertThat(a).isNotEqualTo(b).isNotEqualTo(c);
        assertThat(b).isNotEqualTo(c);
        assertThat(id(new PingRequests().liveBody())).isEqualTo(a);
    }

    @Test
    void sessionPingsCarryTheSessionAndAnonymousPingsDoNot() {
        PingRequests pings = new PingRequests();

        assertThat(pings.withSession(FakeMcpServer.LEGACY_VERSION, "s").headers()).containsEntry("MCP-Session-Id", "s");
        assertThat(pings.withoutSession(FakeMcpServer.LEGACY_VERSION).headers()).doesNotContainKey("MCP-Session-Id");
        assertThat(JsonRpcEnvelope.of(pings.liveBody()).method()).contains("ping");
    }

    private static long id(String body) {
        return JsonRpcEnvelope.of(body).id().orElseThrow().asLong();
    }
}
