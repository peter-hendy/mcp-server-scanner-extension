package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;
import java.util.function.Supplier;

public final class WithholdingAfterTooManyRequests extends TooManyRequestsTrip {

    public WithholdingAfterTooManyRequests(Http delegate) {
        super(delegate);
    }

    @Override
    protected HttpRequest outgoing(HttpRequest request) {
        return request;
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request) {
        return unlessTripped(request, () -> super.sendRequest(request));
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, HttpMode httpMode) {
        return unlessTripped(request, () -> super.sendRequest(request, httpMode));
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, HttpMode httpMode, String connectionId) {
        return unlessTripped(request, () -> super.sendRequest(request, httpMode, connectionId));
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, RequestOptions requestOptions) {
        return unlessTripped(request, () -> super.sendRequest(request, requestOptions));
    }

    @Override
    public List<HttpRequestResponse> sendRequests(List<HttpRequest> requests) {
        return requests.stream().map(this::sendRequest).toList();
    }

    @Override
    public List<HttpRequestResponse> sendRequests(List<HttpRequest> requests, HttpMode httpMode) {
        return requests.stream().map(request -> sendRequest(request, httpMode)).toList();
    }

    private HttpRequestResponse unlessTripped(HttpRequest request, Supplier<HttpRequestResponse> send) {
        return tripped() ? HttpRequestResponse.httpRequestResponse(request, null) : send.get();
    }
}
