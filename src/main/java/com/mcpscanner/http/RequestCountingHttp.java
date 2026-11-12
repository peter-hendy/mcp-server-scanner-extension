package com.mcpscanner.http;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

public final class RequestCountingHttp extends ForwardingHttp {

    private final Runnable requestSent;

    public RequestCountingHttp(Http delegate, Runnable requestSent) {
        super(delegate);
        this.requestSent = requestSent;
    }

    @Override
    protected HttpRequest outgoing(HttpRequest request) {
        requestSent.run();
        return request;
    }

    @Override
    public void sentOutsideBurp(HttpRequestResponse exchange) {
        requestSent.run();
        super.sentOutsideBurp(exchange);
    }
}
