package com.mcpscanner.http;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Objects;
import java.util.function.UnaryOperator;

public final class CountingHttp extends ForwardingHttp {

    private final ExchangeCounter counter;
    private final UnaryOperator<HttpRequest> outgoing;

    private CountingHttp(Http delegate, ExchangeCounter counter, UnaryOperator<HttpRequest> outgoing) {
        super(delegate);
        this.counter = Objects.requireNonNull(counter);
        this.outgoing = Objects.requireNonNull(outgoing);
    }

    public static Http counting(Http http, ExchangeCounter counter) {
        return marking(http, counter, UnaryOperator.identity());
    }

    public static Http marking(Http http, ExchangeCounter counter, UnaryOperator<HttpRequest> outgoing) {
        return new CountingHttp(http, counter, outgoing);
    }

    @Override
    protected HttpRequest outgoing(HttpRequest request) {
        return outgoing.apply(request);
    }

    @Override
    protected HttpRequestResponse incoming(HttpRequestResponse exchange) {
        return counter.counted(exchange);
    }

    @Override
    public void sentOutsideBurp(HttpRequestResponse exchange) {
        super.sentOutsideBurp(counter.counted(exchange));
    }
}
