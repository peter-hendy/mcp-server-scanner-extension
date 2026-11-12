package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.message.HttpRequestResponse;

import com.mcpscanner.mcp.TooManyRequests;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

final class ProbeTraffic implements Consumer<HttpRequestResponse> {


    private final List<HttpRequestResponse> exchanges = new ArrayList<>();

    @Override
    public void accept(HttpRequestResponse exchange) {
        exchanges.add(exchange);
    }

    Optional<HttpRequestResponse> lastExchangeOf(Runnable send) {
        int mark = mark();
        send.run();
        return latestSince(mark);
    }

    int mark() {
        return exchanges.size();
    }

    Optional<HttpRequestResponse> latestSince(int mark) {
        return exchanges.size() > mark ? Optional.of(exchanges.get(exchanges.size() - 1)) : Optional.empty();
    }

    Optional<HttpRequestResponse> firstSince(int mark) {
        return exchanges.size() > mark ? Optional.of(exchanges.get(mark)) : Optional.empty();
    }

    boolean throttled() {
        return exchanges.stream().anyMatch(TooManyRequests::answered);
    }

    List<HttpRequestResponse> all() {
        return List.copyOf(exchanges);
    }
}
