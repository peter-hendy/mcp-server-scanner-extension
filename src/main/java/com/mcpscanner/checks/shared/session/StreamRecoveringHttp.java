package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.http.ForwardingHttp;

final class StreamRecoveringHttp extends ForwardingHttp {

    private final Http burp;
    private final StreamingFallback fallback;

    StreamRecoveringHttp(Http burp, StreamingFallback fallback) {
        super(burp);
        this.burp = burp;
        this.fallback = fallback;
    }

    @Override
    protected HttpRequest outgoing(HttpRequest request) {
        return request;
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request) {
        return fallback.send(burp, request, () -> burp.sendRequest(request));
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, RequestOptions requestOptions) {
        return fallback.send(burp, request, () -> burp.sendRequest(request, requestOptions));
    }
}
