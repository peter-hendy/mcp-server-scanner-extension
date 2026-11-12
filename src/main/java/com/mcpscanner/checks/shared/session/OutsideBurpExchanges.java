package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.http.ForwardingHttp;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class OutsideBurpExchanges extends ForwardingHttp {

    private final Set<HttpRequestResponse> sent = Collections.synchronizedSet(identitySet());

    public OutsideBurpExchanges(Http burp) {
        super(burp);
    }

    public Set<HttpRequestResponse> sent() {
        Set<HttpRequestResponse> snapshot = identitySet();
        synchronized (sent) {
            snapshot.addAll(sent);
        }
        return Collections.unmodifiableSet(snapshot);
    }

    @Override
    protected HttpRequest outgoing(HttpRequest request) {
        return request;
    }

    @Override
    public void sentOutsideBurp(HttpRequestResponse exchange) {
        sent.add(exchange);
        super.sentOutsideBurp(exchange);
    }

    private static Set<HttpRequestResponse> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
