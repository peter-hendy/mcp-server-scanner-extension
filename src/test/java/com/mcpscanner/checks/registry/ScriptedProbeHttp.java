package com.mcpscanner.checks.registry;

import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.testutil.SendOnlyBurpHttp;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

final class ScriptedProbeHttp extends SendOnlyBurpHttp {

    private final Function<ProtocolHttpRequest, ProtocolHttpResponse> server;
    private final AtomicInteger sent = new AtomicInteger();

    ScriptedProbeHttp(Function<ProtocolHttpRequest, ProtocolHttpResponse> server) {
        this.server = server;
    }

    int sent() {
        return sent.get();
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, RequestOptions requestOptions) {
        sent.incrementAndGet();
        ProtocolHttpResponse reply = server.apply(protocolRequest(request));
        try (InputStream body = reply.body()) {
            return HttpRequestResponse.httpRequestResponse(request,
                    rawResponse(reply.status(), reply.headers(), body.readAllBytes()));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request) {
        return sendRequest(request, (RequestOptions) null);
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, HttpMode httpMode) {
        return sendRequest(request);
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, HttpMode httpMode, String connectionId) {
        return sendRequest(request);
    }

    @Override
    public List<HttpRequestResponse> sendRequests(List<HttpRequest> requests) {
        return requests.stream().map(this::sendRequest).toList();
    }

    @Override
    public List<HttpRequestResponse> sendRequests(List<HttpRequest> requests, HttpMode httpMode) {
        return sendRequests(requests);
    }

    private static ProtocolHttpRequest protocolRequest(HttpRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (HttpHeader header : request.headers()) {
            headers.put(header.name(), header.value());
        }
        return new ProtocolHttpRequest(URI.create(request.url()), request.method(), headers, request.bodyToString());
    }
}
