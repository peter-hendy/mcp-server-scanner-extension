package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.handler.HttpRequestToBeSent;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BurpObserverAdapterTest {

    private static final String TOOLS_CALL_BODY =
            "{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\"}}";

    private final CapturingSink sink = new CapturingSink();
    private final McpScannerSession session = mock(McpScannerSession.class);

    private BurpObserverAdapter adapter;

    @BeforeAll
    static void installFactory() {
        MontoyaTestFactory.install();
    }

    @BeforeEach
    void setUp() {
        lenient().when(session.transportType()).thenReturn(TransportType.STREAMABLE_HTTP);
        adapter = new BurpObserverAdapter(sink, session);
    }

    @Test
    void mcpRequestIsObservedOnceWithParsedMethodIdSessionAndTransport() {
        adapter.observeRequest(jsonRpcRequest(TOOLS_CALL_BODY, "session-abc"));

        assertThat(sink.messages).hasSize(1);
        ObservedMessage observed = sink.messages.get(0);
        assertThat(observed.direction()).isEqualTo(Direction.CLIENT_TO_SERVER);
        assertThat(observed.method()).isEqualTo("tools/call");
        assertThat(observed.jsonrpcId()).isEqualTo("42");
        assertThat(observed.sessionId()).isEqualTo("session-abc");
        assertThat(observed.transport()).isEqualTo(TransportType.STREAMABLE_HTTP);
        assertThat(observed.status()).isNull();
        assertThat(observed.parsed()).isNotNull();
        assertThat(observed.parsed().path("method").asText()).isEqualTo("tools/call");
    }

    @Test
    void sessionIdIsReadCaseInsensitivelyAndNullWhenAbsent() {
        adapter.observeRequest(jsonRpcRequestWithoutSession(TOOLS_CALL_BODY));

        assertThat(sink.messages).hasSize(1);
        assertThat(sink.messages.get(0).sessionId()).isNull();
    }

    @Test
    void nonMcpRequestIsNotObserved() {
        adapter.observeRequest(plainRequest("<html><body>not json-rpc</body></html>"));

        assertThat(sink.messages).isEmpty();
    }

    @Test
    void malformedJsonBodyDoesNotThrowAndIsNotObserved() {
        adapter.observeRequest(plainRequest("{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\""));

        assertThat(sink.messages).isEmpty();
    }

    private HttpRequestToBeSent jsonRpcRequest(String body, String sessionId) {
        HttpRequestToBeSent request = postRequest(body);
        when(request.headerValue("Mcp-Session-Id")).thenReturn(sessionId);
        return request;
    }

    private HttpRequestToBeSent jsonRpcRequestWithoutSession(String body) {
        HttpRequestToBeSent request = postRequest(body);
        when(request.headerValue("Mcp-Session-Id")).thenReturn(null);
        return request;
    }

    private HttpRequestToBeSent plainRequest(String body) {
        HttpRequestToBeSent request = postRequest(body);
        lenient().when(request.headerValue("Mcp-Session-Id")).thenReturn(null);
        return request;
    }

    private HttpRequestToBeSent postRequest(String body) {
        HttpRequestToBeSent request = mock(HttpRequestToBeSent.class);
        lenient().when(request.method()).thenReturn("POST");
        lenient().when(request.bodyToString()).thenReturn(body);
        return request;
    }

    private static final class CapturingSink implements McpObservationSink {
        private final List<ObservedMessage> messages = new ArrayList<>();

        @Override
        public void observe(ObservedMessage message) {
            messages.add(message);
        }
    }
}
