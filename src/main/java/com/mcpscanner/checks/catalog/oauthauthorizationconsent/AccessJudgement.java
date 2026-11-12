package com.mcpscanner.checks.catalog.oauthauthorizationconsent;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.List;

record AccessJudgement(Access access, HttpRequestResponse anonymous, HttpRequestResponse withToken) {

    List<HttpRequestResponse> evidence() {
        return List.of(anonymous, withToken);
    }

    String anonymousStatus() {
        return status(anonymous);
    }

    String tokenStatus() {
        return status(withToken);
    }

    private static String status(HttpRequestResponse exchange) {
        return exchange.response() == null ? "no answer" : "HTTP " + exchange.response().statusCode();
    }
}
