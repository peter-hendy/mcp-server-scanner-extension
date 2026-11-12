package com.mcpscanner.client;

import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.core.protocol.McpResponses.Reply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpClientManagerCompletionTest {

    private static final CompletionRequest REQUEST =
            new CompletionRequest(new CompletionReference.Prompt("p"), "a", "");

    private final McpScannerSession session = mock(McpScannerSession.class);
    private final McpClientManager manager = new McpClientManager(McpEventLog.noop(), session);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        when(session.resolvedEndpoint()).thenReturn("https://example.test/mcp");
        when(session.catalog(anyString(), eq(null))).thenAnswer(invocation -> emptyCatalog(invocation.getArgument(0)));
    }

    @Test
    void completesOverTheSessionWhenTheServerAdvertisesCompletions() {
        connectAdvertising(Map.of("completions", Map.of()));
        when(session.call("completion/complete", REQUEST.paramsJson()))
                .thenReturn(new Reply(9, Map.of("completion", Map.of("values", List.of("x"))), Map.of()));

        assertThat(manager.completionsAdvertised()).isTrue();
        assertThat(manager.complete(REQUEST)).isEqualTo(new CompletionOutcome.Values(List.of("x"), null, null));
    }

    @Test
    void sendsNothingWhenTheServerDoesNotAdvertiseCompletions() {
        connectAdvertising(Map.of("prompts", Map.of()));

        assertThat(manager.completionsAdvertised()).isFalse();
        assertThat(manager.complete(REQUEST)).isEqualTo(new CompletionOutcome.NotAdvertised());
        verify(session, never()).call(anyString(), anyString());
    }

    @Test
    void sendsNothingWhileDisconnected() {
        assertThat(manager.completionsAdvertised()).isFalse();
        assertThat(manager.complete(REQUEST)).isEqualTo(new CompletionOutcome.Failed("Not connected to an MCP server"));
        verify(session, never()).call(anyString(), anyString());
    }

    @Test
    void forgetsTheAdvertisedCapabilityOnDisconnect() {
        connectAdvertising(Map.of("completions", Map.of()));

        manager.disconnect();

        assertThat(manager.completionsAdvertised()).isFalse();
        assertThat(manager.complete(REQUEST)).isInstanceOf(CompletionOutcome.Failed.class);
        verify(session, never()).call(anyString(), anyString());
    }

    private void connectAdvertising(Map<String, Object> capabilities) {
        when(session.admissionEnvelope()).thenReturn(Map.of("result", Map.of("capabilities", capabilities)));
        manager.connect(new McpServerConfig("https://example.test/mcp", TransportType.MODERN_HTTP,
                new NoAuthStrategy()));
    }

    private static Reply emptyCatalog(String method) {
        String key = switch (method) {
            case "tools/list" -> "tools";
            case "resources/list" -> "resources";
            case "resources/templates/list" -> "resourceTemplates";
            default -> "prompts";
        };
        return new Reply(2, Map.of(key, List.of()), Map.of());
    }
}
