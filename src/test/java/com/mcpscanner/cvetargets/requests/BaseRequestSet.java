package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.mcp.SseProbeStreams;

import java.util.List;
import java.util.Optional;

public interface BaseRequestSet extends AutoCloseable {

    List<HttpRequestResponse> requests();

    int unansweredCount();

    default Http route(Http http) {
        return http;
    }

    default boolean reachedTarget(HttpRequestResponse exchange) {
        return exchange != null && exchange.response() != null;
    }

    default Optional<HttpService> probeService() {
        return Optional.empty();
    }

    default Optional<SseProbeStreams> sseProbeStreams() {
        return Optional.empty();
    }

    @Override
    void close();
}
