package com.mcpscanner.proxy;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.internal.ObjectFactoryLocator;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Http1LoopbackRelayTest {

    private final Http http = mock(Http.class);
    private final HttpService loopback = mock(HttpService.class);
    private final HttpRequest http2Request = mock(HttpRequest.class);
    private final HttpRequest http1Request = mock(HttpRequest.class);
    private final HttpRequest relayedRequest = mock(HttpRequest.class);
    private final ByteArray body = mock(ByteArray.class);
    private final Http1LoopbackRelay relay = new Http1LoopbackRelay(http);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        when(http2Request.httpVersion()).thenReturn("HTTP/2");
        when(http2Request.method()).thenReturn("POST");
        when(http2Request.path()).thenReturn("/mcp?x=1");
        when(http2Request.body()).thenReturn(body);
        List<HttpHeader> headers = List.of(
                header(":authority", "mcp.example.com"),
                header("host", "mcp.example.com"),
                header("content-type", "application/json"),
                header("content-length", "17"),
                header("connection", "keep-alive"),
                header("x-mcp-scanner-audit-capability", "signed"));
        when(http2Request.headers()).thenReturn(headers);
        when(ObjectFactoryLocator.FACTORY.httpRequest(same(loopback), anyString())).thenReturn(http1Request);
        when(http1Request.withBody(body)).thenReturn(relayedRequest);
        when(http.sendRequest(relayedRequest, HttpMode.HTTP_1)).thenReturn(mock(HttpRequestResponse.class));
    }

    @Test
    void relaysHttp2RequestsEvenFromTheScanner() {
        assertThat(relay.needsRelay(toBeSent("HTTP/2", ToolType.SCANNER))).isTrue();
    }

    @Test
    void relaysHttp1RequestsFromManualToolsBecauseTheirHttpModeIsNotVisible() {
        assertThat(relay.needsRelay(toBeSent("HTTP/1.1", ToolType.REPEATER))).isTrue();
        assertThat(relay.needsRelay(toBeSent("HTTP/1.1", ToolType.EXTENSIONS))).isTrue();
    }

    @Test
    void leavesScannerHttp1RequestsToBurpSoResponseTimingStaysReal() {
        assertThat(relay.needsRelay(toBeSent("HTTP/1.1", ToolType.SCANNER))).isFalse();
    }

    @Test
    void resendsTheRequestToTheLoopbackAsHttp1WithItsRegularHeadersClosingTheConnection() {
        List<String> rendered = new java.util.ArrayList<>();
        when(ObjectFactoryLocator.FACTORY.httpRequest(same(loopback), anyString())).thenAnswer(call -> {
            rendered.add(call.getArgument(1));
            return http1Request;
        });

        relay.relay(http2Request, loopback);

        assertThat(rendered).containsExactly(
                "POST /mcp?x=1 HTTP/1.1\r\n"
                        + "host: mcp.example.com\r\n"
                        + "content-type: application/json\r\n"
                        + "x-mcp-scanner-audit-capability: signed\r\n"
                        + "Connection: close\r\n"
                        + "\r\n");
        verify(http).sendRequest(relayedRequest, HttpMode.HTTP_1);
    }

    @Test
    void returnsTheLoopbackResponse() {
        HttpResponse response = mock(HttpResponse.class);
        HttpRequestResponse exchange = mock(HttpRequestResponse.class);
        when(exchange.response()).thenReturn(response);
        when(http.sendRequest(relayedRequest, HttpMode.HTTP_1)).thenReturn(exchange);

        assertThat(relay.relay(http2Request, loopback)).contains(response);
    }

    @Test
    void returnsEmptyWhenTheLoopbackGivesNoResponse() {
        assertThat(relay.relay(http2Request, loopback)).isEmpty();
    }

    private static HttpRequestToBeSent toBeSent(String version, ToolType tool) {
        HttpRequestToBeSent request = mock(HttpRequestToBeSent.class);
        ToolSource source = mock(ToolSource.class);
        when(source.isFromTool(ToolType.SCANNER)).thenReturn(tool == ToolType.SCANNER);
        when(request.toolSource()).thenReturn(source);
        when(request.httpVersion()).thenReturn(version);
        return request;
    }

    private static HttpHeader header(String name, String value) {
        HttpHeader header = mock(HttpHeader.class);
        when(header.name()).thenReturn(name);
        when(header.value()).thenReturn(value);
        return header;
    }
}
