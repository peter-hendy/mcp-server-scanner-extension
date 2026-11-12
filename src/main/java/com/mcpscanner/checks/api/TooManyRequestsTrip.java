package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.http.ForwardingHttp;
import com.mcpscanner.mcp.TooManyRequests;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class TooManyRequestsTrip extends ForwardingHttp {

    private final AtomicBoolean tripped;

    protected TooManyRequestsTrip(Http delegate) {
        this(delegate, new AtomicBoolean());
    }

    protected TooManyRequestsTrip(Http delegate, AtomicBoolean tripped) {
        super(delegate);
        this.tripped = tripped;
    }

    public final boolean tripped() {
        return tripped.get();
    }

    @Override
    protected final HttpRequestResponse incoming(HttpRequestResponse exchange) {
        if (TooManyRequests.answered(exchange)) {
            tripped.set(true);
        }
        return exchange;
    }
}
