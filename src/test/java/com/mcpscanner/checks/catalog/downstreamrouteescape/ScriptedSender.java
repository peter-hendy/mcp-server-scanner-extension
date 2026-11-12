package com.mcpscanner.checks.catalog.downstreamrouteescape;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class ScriptedSender implements ValueSender {

    private final Function<String, HttpRequestResponse> fallback;
    private final Map<String, Deque<HttpRequestResponse>> scripted = new HashMap<>();
    final List<String> sent = new ArrayList<>();

    ScriptedSender(Function<String, HttpRequestResponse> fallback) {
        this.fallback = fallback;
    }

    ScriptedSender then(String value, HttpRequestResponse reply) {
        scripted.computeIfAbsent(value, ignored -> new ArrayDeque<>()).add(reply);
        return this;
    }

    @Override
    public HttpRequestResponse send(String value) {
        sent.add(value);
        Deque<HttpRequestResponse> queue = scripted.get(value);
        return queue == null || queue.isEmpty() ? fallback.apply(value) : queue.poll();
    }
}
