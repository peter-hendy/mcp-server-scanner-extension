package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.mcpscanner.checks.api.crossidentity.FakeMcpTarget.method;
import static com.mcpscanner.checks.api.crossidentity.FakeMcpTarget.principal;

public final class CallerServer implements Function<HttpRequest, FakeReply> {

    private final boolean stateful;
    private final Function<String, FakeReply> callAs;
    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    private final Set<String> refused = ConcurrentHashMap.newKeySet();
    private final AtomicInteger next = new AtomicInteger();
    private volatile boolean anonymousSessions;

    private CallerServer(boolean stateful, Function<String, FakeReply> callAs) {
        this.stateful = stateful;
        this.callAs = callAs;
    }

    public static CallerServer stateless(Function<String, FakeReply> callAs) {
        return new CallerServer(false, callAs);
    }

    public static CallerServer stateful(Function<String, FakeReply> callAs) {
        return new CallerServer(true, callAs);
    }

    public CallerServer admittingAnonymousSessions() {
        anonymousSessions = true;
        return this;
    }

    public void refuse(String caller) {
        refused.add(caller);
    }

    public String issuedTo(String sessionId) {
        return sessions.get(sessionId);
    }

    public Set<String> issued() {
        return Set.copyOf(sessions.keySet());
    }

    @Override
    public FakeReply apply(HttpRequest request) {
        String caller = principal(request);
        if ("DELETE".equals(request.method())) {
            return FakeReply.accepted();
        }
        if (caller != null && refused.contains(caller)) {
            return FakeReply.unauthorized();
        }
        return switch (method(request)) {
            case "initialize" -> caller == null && !anonymousSessions ? FakeReply.unauthorized()
                    : FakeReply.initialized(stateful ? issue(caller) : null);
            case "notifications/initialized" -> FakeReply.accepted();
            default -> caller == null ? FakeReply.unauthorized() : callAs.apply(caller);
        };
    }

    private String issue(String caller) {
        String sessionId = "sess-" + next.incrementAndGet();
        sessions.put(sessionId, caller == null ? "" : caller);
        return sessionId;
    }
}
