package com.mcpscanner.http;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.HttpMode;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

interface ConnectionSender {

    HttpRequestResponse fresh(HttpRequest request);

    HttpRequestResponse reusing(HttpRequest request, String connectionId);

    static ConnectionSender plain(Http http) {
        return new ConnectionSender() {
            @Override
            public HttpRequestResponse fresh(HttpRequest request) {
                return http.sendRequest(request);
            }

            @Override
            public HttpRequestResponse reusing(HttpRequest request, String connectionId) {
                return http.sendRequest(request, HttpMode.HTTP_1, connectionId);
            }
        };
    }

    static ConnectionSender withOptions(Http http, RequestOptions options) {
        return new ConnectionSender() {
            @Override
            public HttpRequestResponse fresh(HttpRequest request) {
                return http.sendRequest(request, options);
            }

            @Override
            public HttpRequestResponse reusing(HttpRequest request, String connectionId) {
                return http.sendRequest(request, options.withHttpMode(HttpMode.HTTP_1).withConnectionId(connectionId));
            }
        };
    }
}
