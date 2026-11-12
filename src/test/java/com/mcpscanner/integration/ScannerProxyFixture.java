package com.mcpscanner.integration;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.cvetargets.requests.SendOnlyHttp;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.proxy.Http1LoopbackRelay;
import com.mcpscanner.proxy.McpHttpHandler;
import com.mcpscanner.proxy.SseProxyServer;
import com.mcpscanner.proxy.TestSseProxies;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;

record ScannerProxyFixture(McpScannerSession session, SseProxyServer proxy, McpHttpHandler handler)
        implements AutoCloseable {

    static ScannerProxyFixture connect(String endpoint, TransportType transport) throws Exception {
        McpScannerSession session = new McpScannerSession(McpCheckItSupport.realHttpClient(), McpEventLog.noop());
        session.connect(new McpServerConfig(endpoint, transport,
                new BearerTokenAuthStrategy("alice-token")));
        SseProxyServer proxy = TestSseProxies.builder(session).build();
        proxy.start();
        session.setSseProxyPort(proxy::port);
        return new ScannerProxyFixture(session, proxy, new McpHttpHandler(session, proxy, mock(Http1LoopbackRelay.class)));
    }

    Map<String, String> baseHeaders() {
        Map<String, String> headers = new LinkedHashMap<>(session.scannerHeaders());
        headers.putAll(proxy.auditHeaders(session.scannerHeaders()));
        return headers;
    }

    HttpRequestResponse sendBase(Http http, String body) {
        HttpRequest request = HttpRequest.httpRequestFromUrl(session.resolvedEndpoint()).withMethod("POST")
                .withHeader("Content-Type", "application/json")
                .withHeader("Accept", "application/json, text/event-stream");
        for (Map.Entry<String, String> header : baseHeaders().entrySet()) {
            request = request.withHeader(header.getKey(), header.getValue());
        }
        return http.sendRequest(request.withBody(body));
    }

    Http routed(Http delegate) {
        return new SendOnlyHttp() {
            @Override
            public HttpRequestResponse sendRequest(HttpRequest request) {
                return HttpRequestResponse.httpRequestResponse(request,
                        delegate.sendRequest(handler.route(request)).response());
            }
        };
    }

    @Override
    public void close() {
        session.setSseProxyPort(null);
        proxy.stop();
        session.disconnect();
    }
}
