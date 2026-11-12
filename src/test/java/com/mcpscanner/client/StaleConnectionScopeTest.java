package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.config.ClientIdentityProvider;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.testutil.FakeMcpServer;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.testutil.ScriptedProtocolTransport;
import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaleConnectionScopeTest {

    private static final String CLOSED_BEFORE_REPLY = "HTTP/1.1 header parser received no bytes";

    private final AtomicBoolean droppedAdmission = new AtomicBoolean();
    private final AtomicBoolean droppedOperation = new AtomicBoolean();
    private final ScriptedProtocolTransport transport = new ScriptedProtocolTransport(request -> {
        String method = FakeMcpServer.method(request);
        if (isAdmission(method) && droppedAdmission.compareAndSet(false, true)
                || "tools/call".equals(method) && droppedOperation.compareAndSet(false, true)) {
            throw new ScriptedProtocolTransport.ScriptedIoFailure(new IOException(CLOSED_BEFORE_REPLY));
        }
        return FakeMcpServer.respond(request);
    });

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void connectionSetupIsResentOnceButAnOperationIsNeverResent() {
        McpClientManager manager = new McpClientManager(McpEventLog.noop(), ClientIdentityProvider.defaults(),
                transport);
        manager.connect(new McpServerConfig("https://example.test/mcp", TransportType.STREAMABLE_HTTP,
                new NoAuthStrategy()));
        McpScannerSession session = manager.scannerSession();

        assertThatThrownBy(() -> session.sendHttpOperation(
                session.operation("tools/call", "{\"name\":\"echo\",\"arguments\":{}}", 50L)));

        assertThat(transport.methods().stream().filter(StaleConnectionScopeTest::isAdmission).count())
                .isGreaterThanOrEqualTo(2);
        assertThat(transport.methods().stream().filter("tools/call"::equals)).hasSize(1);
        manager.disconnect();
    }

    @Test
    void catalogListingAndHandshakeNotificationOnAClosedKeptAliveConnectionAreResentOnce() {
        ScriptedProtocolTransport dropsEachOnce = new ScriptedProtocolTransport(new DropFirst(
                "notifications/initialized", "tools/list"));
        McpClientManager manager = new McpClientManager(McpEventLog.noop(), ClientIdentityProvider.defaults(),
                dropsEachOnce);

        manager.connect(new McpServerConfig("https://example.test/mcp", TransportType.STREAMABLE_HTTP,
                new NoAuthStrategy()));

        assertThat(dropsEachOnce.methods().stream().filter("notifications/initialized"::equals)).hasSize(2);
        assertThat(dropsEachOnce.methods().stream().filter("tools/list"::equals)).hasSize(2);
        manager.disconnect();
    }

    @Test
    void aListedReadOnlyToolCallOnTheSessionIsResentOnce() throws Exception {
        ScriptedProtocolTransport dropsFirstCall = new ScriptedProtocolTransport(new DropFirst("tools/call"));
        McpClientManager manager = new McpClientManager(McpEventLog.noop(), ClientIdentityProvider.defaults(),
                dropsFirstCall);
        manager.connect(new McpServerConfig("https://example.test/mcp", TransportType.STREAMABLE_HTTP,
                new NoAuthStrategy()));
        McpScannerSession session = manager.scannerSession();
        session.resendableRequests().learn(List.of(new McpToolDefinition("get_note", "", "{}")));

        session.sendHttpOperation(session.operation("tools/call", "{\"name\":\"get_note\",\"arguments\":{}}", 50L));

        assertThat(dropsFirstCall.methods().stream().filter("tools/call"::equals)).hasSize(2);
        manager.disconnect();
    }

    private static final class DropFirst implements Function<ProtocolHttpRequest, ProtocolHttpResponse> {
        private final Set<String> undropped = ConcurrentHashMap.newKeySet();

        DropFirst(String... methods) {
            undropped.addAll(List.of(methods));
        }

        @Override
        public ProtocolHttpResponse apply(ProtocolHttpRequest request) {
            if (undropped.remove(FakeMcpServer.method(request))) {
                throw new ScriptedProtocolTransport.ScriptedIoFailure(new IOException(CLOSED_BEFORE_REPLY));
            }
            return FakeMcpServer.respond(request);
        }
    }

    private static boolean isAdmission(String method) {
        return "initialize".equals(method) || "server/discover".equals(method);
    }
}
