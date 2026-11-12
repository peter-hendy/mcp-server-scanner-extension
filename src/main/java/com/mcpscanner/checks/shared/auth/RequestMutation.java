package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Objects;
import java.util.function.UnaryOperator;

public record RequestMutation(String value, UnaryOperator<HttpRequest> operator) {

    public RequestMutation {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(operator, "operator");
    }

    public HttpRequest applyTo(HttpRequest request) {
        return operator.apply(request);
    }

    public RequestMutation after(UnaryOperator<HttpRequest> preparation) {
        return new RequestMutation(value, request -> operator.apply(preparation.apply(request)));
    }
}
