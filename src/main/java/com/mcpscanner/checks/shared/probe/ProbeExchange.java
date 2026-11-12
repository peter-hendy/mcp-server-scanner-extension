package com.mcpscanner.checks.shared.probe;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.ArrayList;
import java.util.List;

public record ProbeExchange(List<HttpRequestResponse> exchanges, boolean conclusive) {

    public ProbeExchange {
        exchanges = List.copyOf(exchanges);
        if (exchanges.isEmpty()) {
            throw new IllegalArgumentException("a probe exchange needs at least one exchange");
        }
    }

    public ProbeExchange(List<HttpRequestResponse> exchanges) {
        this(exchanges, true);
    }

    public static ProbeExchange of(HttpRequestResponse... exchanges) {
        return new ProbeExchange(List.of(exchanges));
    }

    public static ProbeExchange interrupted(HttpRequestResponse exchange) {
        return new ProbeExchange(List.of(exchange), false);
    }

    public ProbeExchange followedBy(HttpRequestResponse exchange) {
        List<HttpRequestResponse> followed = new ArrayList<>(exchanges);
        followed.add(exchange);
        return new ProbeExchange(followed, conclusive);
    }

    public HttpRequestResponse last() {
        return exchanges.get(exchanges.size() - 1);
    }
}
