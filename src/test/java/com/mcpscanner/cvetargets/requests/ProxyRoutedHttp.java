package com.mcpscanner.cvetargets.requests;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.function.UnaryOperator;

final class ProxyRoutedHttp extends SendOnlyHttp {

    private final Http delegate;
    private final UnaryOperator<HttpRequest> deliveryRoute;

    ProxyRoutedHttp(Http delegate, UnaryOperator<HttpRequest> deliveryRoute) {
        this.delegate = delegate;
        this.deliveryRoute = deliveryRoute;
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request) {
        HttpRequestResponse exchange = delegate.sendRequest(deliveryRoute.apply(request));
        return HttpRequestResponse.httpRequestResponse(request, exchange.response());
    }
}
