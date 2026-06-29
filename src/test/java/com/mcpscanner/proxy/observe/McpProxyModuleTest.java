package com.mcpscanner.proxy.observe;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.TransportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpProxyModuleTest {

    private final MontoyaApi api = mock(MontoyaApi.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    private final McpScannerSession session = mock(McpScannerSession.class);
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final List<McpExchange> tabFeed = new ArrayList<>();
    private final List<McpExchange> scanned = new ArrayList<>();

    private McpProxyModule module;

    @BeforeEach
    void setUp() {
        lenient().when(session.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        module = new McpProxyModule(api, enabled::get, session, scanned::add, tabFeed::add);
    }

    @Test
    void preflightIsExposed() {
        assertThat(module.preflight()).isNotNull();
    }

    @Test
    void exchangeLogIsExposedForDisconnectClearing() {
        assertThat(module.exchangeLog()).isNotNull();
    }

    @Test
    void swapPolicyOffSwapsAllMatchingToolsWithoutReadingToolSource() {
        enabled.set(false);
        HttpRequestToBeSent request = mock(HttpRequestToBeSent.class);

        assertThat(module.swapPolicy().shouldSwap(request)).isTrue();
        verify(request, never()).toolSource();
    }

    @Test
    void swapPolicyOnNarrowsToScannerFamily() {
        enabled.set(true);
        HttpRequestToBeSent scannerRequest = requestFromScannerFamily(true);
        HttpRequestToBeSent proxyRequest = requestFromScannerFamily(false);

        assertThat(module.swapPolicy().shouldSwap(scannerRequest)).isTrue();
        assertThat(module.swapPolicy().shouldSwap(proxyRequest)).isFalse();
    }

    @Test
    void appendedRowsFlowToTheTabFeed() {
        module.exchangeLog().observe(clientToServer("s", "1", "tools/call"));

        assertThat(tabFeed).hasSize(1);
        assertThat(tabFeed.get(0).method()).isEqualTo("tools/call");
    }

    @Test
    void injectedPassiveRunnerScansResponseRows() {
        module.exchangeLog().observe(clientToServer("s", "9", "tools/call"));
        module.exchangeLog().observe(serverToClient("s", "9", 200));

        assertThat(scanned).hasSize(1);
        assertThat(scanned.get(0).direction()).isEqualTo(Direction.SERVER_TO_CLIENT);
    }

    @Test
    void trafficObserverOffDoesNotRecordToTheLog() {
        enabled.set(false);

        module.trafficObserver().observeResponse(mock(burp.api.montoya.http.handler.HttpResponseReceived.class));

        assertThat(module.exchangeLog().exchanges()).isEmpty();
    }

    private HttpRequestToBeSent requestFromScannerFamily(boolean isScannerFamily) {
        HttpRequestToBeSent request = mock(HttpRequestToBeSent.class);
        ToolSource toolSource = mock(ToolSource.class);
        when(toolSource.isFromTool(ToolType.REPEATER, ToolType.SCANNER, ToolType.INTRUDER))
                .thenReturn(isScannerFamily);
        when(request.toolSource()).thenReturn(toolSource);
        return request;
    }

    private static ObservedMessage clientToServer(String sessionId, String id, String method) {
        return new ObservedMessage(Direction.CLIENT_TO_SERVER, TransportType.STREAMABLE_HTTP, sessionId, id,
                method, mock(HttpRequest.class), mock(JsonNode.class), null);
    }

    private static ObservedMessage serverToClient(String sessionId, String id, Integer status) {
        return new ObservedMessage(Direction.SERVER_TO_CLIENT, TransportType.STREAMABLE_HTTP, sessionId, id,
                null, null, mock(JsonNode.class), status);
    }
}
