package com.mcpscanner.http;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Objects;

public final class ConnectionReusingHttp extends ForwardingHttp {

    private final ConnectionReuse reuse;

    private ConnectionReusingHttp(Http delegate, ConnectionReuse reuse) {
        super(delegate);
        this.reuse = Objects.requireNonNull(reuse);
    }

    public static Http around(Http http, ConnectionReuse reuse) {
        return http instanceof ConnectionReusingHttp ? http : new ConnectionReusingHttp(http, reuse);
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request) {
        return reuse.send(request, ConnectionSender.plain(delegate()));
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, RequestOptions requestOptions) {
        return reuse.send(request, ConnectionSender.withOptions(delegate(), requestOptions));
    }

    @Override
    public HttpRequestResponse sendRequest(HttpRequest request, HttpMode httpMode, String connectionId) {
        return delegate().sendRequest(request, httpMode, connectionId);
    }

    @Override
    protected HttpRequest outgoing(HttpRequest request) {
        return ConnectionClose.on(request);
    }
}
