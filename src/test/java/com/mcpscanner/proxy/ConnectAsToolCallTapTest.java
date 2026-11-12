package com.mcpscanner.proxy;

import com.mcpscanner.mcp.ScannerSentinels;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectAsToolCallTapTest {

    private static final String TOOL_CALL = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\"}";
    private static final ProxyResponse REPLY = new ProxyResponse(401, "application/json", "{}", Map.of());
    private static final AuditRequest MANAGED = new AuditRequest(AuditProvenance.Status.MANAGED, null);
    private static final AuditRequest MANUAL = new AuditRequest(AuditProvenance.Status.MANUAL, null);

    private final List<Integer> observed = new ArrayList<>();
    private final ConnectAsToolCallTap tap = new ConnectAsToolCallTap((status, body) -> observed.add(status));

    @Test
    void observesAManagedToolCallSentAsTheConnectAsUser() {
        tap.observe(TOOL_CALL, Map.of(), MANAGED, REPLY);

        assertThat(observed).containsExactly(401);
    }

    @Test
    void ignoresAManualToolCallCarryingItsOwnCredentials() {
        tap.observe(TOOL_CALL, Map.of("authorization", "Bearer expired"), MANUAL, REPLY);

        assertThat(observed).isEmpty();
    }

    @Test
    void ignoresAToolCallSentAsAnotherIdentity() {
        tap.observe(TOOL_CALL, Map.of(ScannerSentinels.IDENTITY_HEADER.toLowerCase(), "bob"), MANAGED, REPLY);

        assertThat(observed).isEmpty();
    }

    @Test
    void ignoresAToolCallWithItsCredentialsStripped() {
        tap.observe(TOOL_CALL, Map.of(ScannerSentinels.STRIP_AUTH_HEADER.toLowerCase(), "1"), MANAGED, REPLY);

        assertThat(observed).isEmpty();
    }

    @Test
    void ignoresRequestsOtherThanToolCalls() {
        tap.observe("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", Map.of(), MANAGED, REPLY);

        assertThat(observed).isEmpty();
    }

    @Test
    void passesAnAbsentBodyAsEmpty() {
        List<String> bodies = new ArrayList<>();
        new ConnectAsToolCallTap((status, body) -> bodies.add(body))
                .observe(TOOL_CALL, Map.of(), MANAGED, new ProxyResponse(502, "application/json", null, Map.of()));

        assertThat(bodies).containsExactly("");
    }
}
