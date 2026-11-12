package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.mcp.McpOperationFraming;
import com.mcpscanner.mcp.ResendableRequests;
import com.mcpscanner.mcp.SseProbeStreams;

import java.util.Map;
import java.util.Optional;

interface TransportRoute extends AutoCloseable {

    int TOO_MANY_REQUESTS = 429;

    TransportRoute DIRECT = new TransportRoute() {
        @Override
        public Map<String, String> sessionHeaders(Map<String, String> scannerHeaders) {
            return scannerHeaders;
        }

        @Override
        public Http route(Http http) {
            return http;
        }

        @Override
        public boolean reachedTarget(HttpRequestResponse exchange) {
            return exchange != null && exchange.response() != null
                    && exchange.response().statusCode() != TOO_MANY_REQUESTS;
        }

        @Override
        public Optional<HttpService> probeService() {
            return Optional.empty();
        }

        @Override
        public void close() {
        }
    };

    static TransportRoute forSession(McpScannerSession session, McpOperationFraming.Framer operationFramer,
                                     ResendableRequests resendableRequests) {
        return ProxyRoute.start(session, operationFramer, resendableRequests);
    }

    Map<String, String> sessionHeaders(Map<String, String> scannerHeaders);

    Http route(Http http);

    boolean reachedTarget(HttpRequestResponse exchange);

    Optional<HttpService> probeService();

    default Optional<SseProbeStreams> sseProbeStreams() {
        return Optional.empty();
    }

    @Override
    void close();
}
