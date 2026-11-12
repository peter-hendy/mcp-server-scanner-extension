package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.mcp.McpOperationFraming;
import com.mcpscanner.mcp.ResendableRequests;
import com.mcpscanner.mcp.SseProbeStreams;
import com.mcpscanner.proxy.Http1LoopbackRelay;
import com.mcpscanner.proxy.McpHttpHandler;
import com.mcpscanner.proxy.ProxyFabricatedReply;
import com.mcpscanner.proxy.SseProxyServer;
import com.mcpscanner.proxy.TestSseProxies;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;

final class ProxyRoute implements TransportRoute {

    private final McpScannerSession session;
    private final SseProxyServer proxy;
    private final McpHttpHandler handler;

    private ProxyRoute(McpScannerSession session, SseProxyServer proxy) {
        this.session = session;
        this.proxy = proxy;
        this.handler = new McpHttpHandler(session, proxy, mock(Http1LoopbackRelay.class));
    }

    static ProxyRoute start(McpScannerSession session, McpOperationFraming.Framer operationFramer,
                            ResendableRequests resendableRequests) {
        SseProxyServer proxy = TestSseProxies.builder(session).operationFramer(operationFramer)
                .resendableRequests(resendableRequests).build();
        try {
            proxy.start();
        } catch (IOException e) {
            throw new UncheckedIOException("local MCP proxy failed to start", e);
        }
        session.setSseProxyPort(proxy::port);
        return new ProxyRoute(session, proxy);
    }

    @Override
    public Map<String, String> sessionHeaders(Map<String, String> scannerHeaders) {
        Map<String, String> headers = new LinkedHashMap<>(scannerHeaders);
        headers.putAll(proxy.auditHeaders(scannerHeaders));
        return headers;
    }

    @Override
    public Http route(Http http) {
        return new ProxyRoutedHttp(http, handler::route);
    }

    @Override
    public boolean reachedTarget(HttpRequestResponse exchange) {
        return TransportRoute.DIRECT.reachedTarget(exchange) && !ProxyFabricatedReply.matches(exchange.response());
    }

    @Override
    public Optional<HttpService> probeService() {
        return session.probeBaselineService();
    }

    @Override
    public Optional<SseProbeStreams> sseProbeStreams() {
        return session.probeSseStreams();
    }

    @Override
    public void close() {
        session.setSseProxyPort(null);
        proxy.stop();
    }
}
