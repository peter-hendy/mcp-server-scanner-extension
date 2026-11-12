package com.mcpscanner.checks.catalog.sessionbinding;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.crossidentity.FakeMcpTarget;
import com.mcpscanner.checks.api.crossidentity.FakeReply;
import com.mcpscanner.checks.api.crossidentity.FakeServers;
import com.mcpscanner.checks.api.crossidentity.TestPrincipals;
import com.mcpscanner.checks.shared.host.Claims;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import static com.mcpscanner.checks.api.crossidentity.FakeMcpTarget.toolCall;
import static com.mcpscanner.checks.catalog.sessionbinding.SessionBindingHarness.withPeerB;
import static org.assertj.core.api.Assertions.assertThat;

class SessionBindingClaimTest {

    private static final int SCANNED_ITEMS = 35;

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    private static HttpRequest readOnlyCall(int index) {
        return toolCall("read_record_" + index, "{}", "A").withAddedHeader("Mcp-Session-Id", "sess-A");
    }

    private static void scan(SessionBindingHarness harness, int items) {
        IntStream.range(0, items).forEach(index -> harness.audit(readOnlyCall(index)));
    }

    private static long handshakes(SessionBindingHarness harness) {
        return harness.sent().stream().filter(request -> "initialize".equals(FakeMcpTarget.method(request))).count();
    }

    private static long peerOnLiveSession(SessionBindingHarness harness, String token) {
        return harness.sent().stream().filter(request -> "sess-A".equals(request.headerValue("Mcp-Session-Id"))
                && token.equals(FakeMcpTarget.principal(request))).count();
    }

    @Test
    void aThirtyFiveItemStatefulScanProbesEachHostOnceForTheComparedIdentity() {
        var single = withPeerB().against(FakeServers.sessions(true));
        scan(single, 1);
        var full = withPeerB().against(FakeServers.sessions(true));
        scan(full, SCANNED_ITEMS);

        assertThat(full.sent()).hasSameSizeAs(single.sent());
        assertThat(handshakes(full)).isEqualTo(handshakes(single)).isPositive();
        assertThat(peerOnLiveSession(full, "B")).isEqualTo(1);
        assertThat(full.decisions()).containsOnlyOnce("session_binding:peer_denied");
    }

    @Test
    void aSecondComparedIdentityGetsItsOwnSingleRun() {
        var harness = withPeerB().with(TestPrincipals.peer("C")).against(FakeServers.sessions(true));
        scan(harness, SCANNED_ITEMS);

        assertThat(harness.decisionsOf("identity-B")).containsOnlyOnce("session_binding:peer_denied");
        assertThat(harness.decisionsOf("identity-C")).containsOnlyOnce("session_binding:peer_denied");
        assertThat(peerOnLiveSession(harness, "B")).isEqualTo(1);
        assertThat(peerOnLiveSession(harness, "C")).isEqualTo(1);
    }

    @Test
    void anUnconfirmedWriteDoesNotConsumeTheClaim() {
        HttpRequest write = toolCall("delete_record", "{}", "A").withAddedHeader("Mcp-Session-Id", "sess-A");
        var harness = withPeerB().permitting(item -> !item.name().equals("delete_record"))
                .against(FakeServers.sessions(true));

        harness.audit(write);
        assertThat(harness.sent()).isEmpty();
        assertThat(harness.decisions()).contains("write_replay_not_confirmed");

        harness.audit(readOnlyCall(0));
        assertThat(harness.decisions()).contains("session_binding:peer_denied");
    }

    @Test
    void disconnectAllowsARerun() {
        var harness = withPeerB().against(FakeServers.sessions(true));
        scan(harness, 2);
        harness.disconnect();
        harness.audit(readOnlyCall(2));

        assertThat(Collections.frequency(harness.decisions(), "session_binding:peer_denied")).isEqualTo(2);
    }

    @Test
    void aConclusiveFindingKeepsTheClaim() {
        var harness = withPeerB().against(FakeServers.sessions(false));
        scan(harness, 3);

        assertThat(harness.issues()).hasSize(1);
    }

    @Test
    void anInterruptedRunReleasesTheClaimUpToTheAttemptLimit() {
        Function<HttpRequest, FakeReply> sessions = FakeServers.sessions(true);
        var harness = withPeerB().against(request -> "B".equals(FakeMcpTarget.principal(request))
                ? new FakeReply(429, "", Map.of()) : sessions.apply(request));
        scan(harness, Claims.MAX_PROBED_ATTEMPTS + 2);

        assertThat(harness.infoLines()).filteredOn(line -> line.contains(" as identity-B (rate limited"))
                .hasSize(Claims.MAX_PROBED_ATTEMPTS);
    }

    @Test
    void itemsWithAnUnusableOwnerBaselineNeverUseUpTheClaim() {
        int unusable = Claims.MAX_UNSUITABLE_SEEDS + 5;
        var sessions = FakeServers.sessions(true);
        var harness = withPeerB().against(request -> "read_record_usable".equals(toolName(request))
                || !"tools/call".equals(FakeMcpTarget.method(request)) ? sessions.apply(request)
                : unusableBaseline(request));
        scan(harness, unusable);

        harness.audit(toolCall("read_record_usable", "{}", "A").withAddedHeader("Mcp-Session-Id", "sess-A"));

        assertThat(Collections.frequency(harness.decisions(), "baseline_empty")).isEqualTo(unusable);
        assertThat(harness.decisions()).contains("session_binding:peer_denied");
    }

    @Test
    void anItemEveryIdentityGetsTheSameResultFromDoesNotUseUpTheClaim() {
        var sessions = FakeServers.sessions(true);
        var harness = withPeerB().against(request -> "help".equals(toolName(request))
                ? sharedDocs(request) : sessions.apply(request));

        harness.audit(toolCall("help", "{}", "A").withAddedHeader("Mcp-Session-Id", "sess-A"));
        harness.audit(readOnlyCall(0));

        assertThat(harness.decisions()).contains("session_binding:identity_gets_owner_result",
                "session_binding:peer_denied");
    }

    private static FakeReply sharedDocs(HttpRequest request) {
        return FakeMcpTarget.principal(request) == null ? FakeReply.unauthorized() : FakeReply.result("docs");
    }

    private static FakeReply unusableBaseline(HttpRequest request) {
        return "initialize".equals(FakeMcpTarget.method(request)) ? FakeReply.initialized("sess-X")
                : FakeReply.result("");
    }

    private static String toolName(HttpRequest request) {
        java.util.regex.Matcher name = java.util.regex.Pattern.compile("\"name\":\"([^\"]+)\"")
                .matcher(request.bodyToString());
        return name.find() ? name.group(1) : "";
    }
}
