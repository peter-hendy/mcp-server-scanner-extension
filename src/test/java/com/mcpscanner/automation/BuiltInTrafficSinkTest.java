package com.mcpscanner.automation;

import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuiltInTrafficSinkTest {

    private final BaseRequestLedger ledger = new BaseRequestLedger();
    private final HttpService sinkService = mock(HttpService.class);
    private final BuiltInTrafficSink sink = new BuiltInTrafficSink(ledger, () -> sinkService);
    private final HttpRequest diverted = mock(HttpRequest.class);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        sink.reset(true);
    }

    @Test
    void divertsAScannerRequestThatNeitherACheckNorTheLaunchSent() {
        HttpRequestToBeSent builtIn = outgoing(ToolType.SCANNER, "{\"x\":\"'\"}");

        assertThat(sink.handleHttpRequestToBeSent(builtIn).request()).isSameAs(diverted);
        assertThat(sink.diverted()).isEqualTo(1);
    }

    @Test
    void sendsTheExtensionsOwnMarkedCheckRequestsToTheTargetWithoutTheMarker() {
        HttpRequestToBeSent probe = outgoing(ToolType.SCANNER, "{\"x\":\"../\"}");
        HttpRequest unmarked = mock(HttpRequest.class);
        when(probe.hasHeader(CheckProbeMarker.HEADER)).thenReturn(true);
        when(probe.withRemovedHeader(CheckProbeMarker.HEADER)).thenReturn(unmarked);

        assertThat(sink.handleHttpRequestToBeSent(probe).request()).isSameAs(unmarked);
        assertThat(sink.diverted()).isZero();
    }

    @Test
    void stripsTheMarkerEvenWhenNotDiverting() {
        sink.reset(false);
        HttpRequestToBeSent probe = outgoing(ToolType.SCANNER, "{}");
        HttpRequest unmarked = mock(HttpRequest.class);
        when(probe.hasHeader(CheckProbeMarker.HEADER)).thenReturn(true);
        when(probe.withRemovedHeader(CheckProbeMarker.HEADER)).thenReturn(unmarked);

        assertThat(sink.handleHttpRequestToBeSent(probe).request()).isSameAs(unmarked);
    }

    @Test
    void sendsTheLaunchedBaseRequestsToTheTarget() {
        HttpRequest launched = BaseRequestLedgerTest.request("POST", "/mcp", "{}");
        ledger.recording(new RenderingOnly(launched)).renderJsonRpc("http://t/mcp", null, java.util.Map.of());
        HttpRequestToBeSent base = outgoing(ToolType.SCANNER, "{}");

        assertThat(sink.handleHttpRequestToBeSent(base).request()).isSameAs(base);
    }

    @Test
    void neverTouchesOtherToolsOrAnInactiveSink() {
        HttpRequestToBeSent proxied = outgoing(ToolType.PROXY, "{\"x\":\"'\"}");
        assertThat(sink.handleHttpRequestToBeSent(proxied).request()).isSameAs(proxied);

        sink.reset(false);
        HttpRequestToBeSent builtIn = outgoing(ToolType.SCANNER, "{\"x\":\"'\"}");
        assertThat(sink.handleHttpRequestToBeSent(builtIn).request()).isSameAs(builtIn);
    }

    @Test
    void resetForgetsTheCountAndTheLaunchedRequests() {
        sink.handleHttpRequestToBeSent(outgoing(ToolType.SCANNER, "{\"x\":\"'\"}"));
        ledger.recording(new RenderingOnly(BaseRequestLedgerTest.request("POST", "/mcp", "{}")))
                .renderJsonRpc("http://t/mcp", null, java.util.Map.of());

        sink.reset(true);

        assertThat(sink.diverted()).isZero();
        assertThat(ledger.contains(BaseRequestLedgerTest.request("POST", "/mcp", "{}"))).isFalse();
    }

    @Test
    void passesEveryResponseThrough() {
        HttpResponseReceived response = mock(HttpResponseReceived.class);

        assertThat(sink.handleHttpResponseReceived(response).response()).isSameAs(response);
    }

    private HttpRequestToBeSent outgoing(ToolType tool, String body) {
        HttpRequestToBeSent request = mock(HttpRequestToBeSent.class);
        ToolSource source = mock(ToolSource.class);
        when(source.isFromTool(any(ToolType[].class))).thenAnswer(call -> {
            for (Object asked : call.getArguments()) {
                if (asked == tool) {
                    return true;
                }
            }
            return false;
        });
        when(request.toolSource()).thenReturn(source);
        when(request.method()).thenReturn("POST");
        when(request.path()).thenReturn("/mcp");
        when(request.bodyToString()).thenReturn(body);
        when(request.withService(sinkService)).thenReturn(diverted);
        return request;
    }

    private record RenderingOnly(HttpRequest rendered) implements com.mcpscanner.scan.request.ScanMessageRenderer {
        @Override
        public HttpRequest renderJsonRpc(String mcpEndpoint, com.mcpscanner.scan.request.McpMessage message,
                                         java.util.Map<String, String> sessionHeaders) {
            return rendered;
        }

        @Override
        public HttpRequest renderClientRegistration(com.mcpscanner.scan.request.ClientRegistrationMessage message) {
            return rendered;
        }
    }
}
