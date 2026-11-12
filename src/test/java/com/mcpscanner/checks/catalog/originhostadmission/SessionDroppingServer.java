package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.testutil.FakeMcpServer;
import com.mcpscanner.testutil.ScriptedBurpHttp;
import com.mcpscanner.testutil.SendOnlyBurpHttp;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

final class SessionDroppingServer {

    static final String DROPPED_SESSION = "dropped-session";

    final Http http = new PlainSendsToo(new ScriptedBurpHttp(request -> Optional.of(respond(request))));
    final List<ProtocolHttpRequest> received = new CopyOnWriteArrayList<>();
    final Set<String> deleted = ConcurrentHashMap.newKeySet();

    private final Set<String> live = ConcurrentHashMap.newKeySet();
    private final AtomicInteger issued = new AtomicInteger();
    private final Function<ProtocolHttpRequest, ProtocolHttpResponse> liveReply;
    private final boolean issuesSessions;

    private SessionDroppingServer(Function<ProtocolHttpRequest, ProtocolHttpResponse> liveReply,
                                  boolean issuesSessions) {
        this.liveReply = liveReply;
        this.issuesSessions = issuesSessions;
    }

    static SessionDroppingServer admittingEveryHeader() {
        return admitting(request -> true);
    }

    static SessionDroppingServer admitting(Predicate<ProtocolHttpRequest> admits) {
        return new SessionDroppingServer(request -> admits.test(request) ? tools(request) : forbidden(request), true);
    }

    static SessionDroppingServer refusingFreshSessionsToo() {
        return new SessionDroppingServer(SessionDroppingServer::forbidden, true);
    }

    static SessionDroppingServer streamingInFreshSessions() {
        return new SessionDroppingServer(request -> {
            throw new IllegalStateException("Streaming response received");
        }, true);
    }

    static SessionDroppingServer issuingNoSessions() {
        return new SessionDroppingServer(SessionDroppingServer::tools, false);
    }

    int sessionsIssued() {
        return issued.get();
    }

    List<String> sessionsUsedFor(String method) {
        return received.stream()
                .filter(request -> Objects.equals(method, FakeMcpServer.method(request)))
                .map(SessionDroppingServer::sessionOf)
                .toList();
    }

    private ProtocolHttpResponse respond(ProtocolHttpRequest request) {
        received.add(request);
        if ("DELETE".equals(request.method())) {
            return delete(request);
        }
        String method = FakeMcpServer.method(request);
        if ("initialize".equals(method)) {
            return initialize(request);
        }
        String session = sessionOf(request);
        if (session == null || !live.contains(session)) {
            return FakeMcpServer.exchange(request, 404, Map.of(), "Session not found");
        }
        if (method == null || method.startsWith("notifications/")) {
            return FakeMcpServer.exchange(request, 202, Map.of(), "");
        }
        return liveReply.apply(request);
    }

    private static ProtocolHttpResponse tools(ProtocolHttpRequest request) {
        return FakeMcpServer.exchange(request, 200, Map.of("Content-Type", List.of("application/json")),
                AdmissionServers.TOOLS_RESULT);
    }

    private static ProtocolHttpResponse forbidden(ProtocolHttpRequest request) {
        return FakeMcpServer.exchange(request, 403, Map.of(), AdmissionServers.FORBIDDEN);
    }

    private ProtocolHttpResponse initialize(ProtocolHttpRequest request) {
        if (!issuesSessions) {
            return FakeMcpServer.exchange(request, 200, Map.of("Content-Type", List.of("application/json")),
                    FakeMcpServer.initializeReply(request));
        }
        String session = "fresh-" + issued.incrementAndGet();
        live.add(session);
        return FakeMcpServer.exchange(request, 200, Map.of("Content-Type", List.of("application/json"),
                "Mcp-Session-Id", List.of(session)), FakeMcpServer.initializeReply(request));
    }

    private ProtocolHttpResponse delete(ProtocolHttpRequest request) {
        String session = sessionOf(request);
        if (session != null && live.remove(session)) {
            deleted.add(session);
            return FakeMcpServer.exchange(request, 200, Map.of(), "");
        }
        return FakeMcpServer.exchange(request, 404, Map.of(), "Session not found");
    }

    static String sessionOf(ProtocolHttpRequest request) {
        return header(request, "Mcp-Session-Id");
    }

    static String header(ProtocolHttpRequest request, String name) {
        return request.headers().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static final class PlainSendsToo extends SendOnlyBurpHttp {

        private final ScriptedBurpHttp scripted;

        PlainSendsToo(ScriptedBurpHttp scripted) {
            this.scripted = scripted;
        }

        @Override
        public HttpRequestResponse sendRequest(HttpRequest request) {
            return scripted.sendRequest(request, RequestOptions.requestOptions());
        }

        @Override
        public HttpRequestResponse sendRequest(HttpRequest request, RequestOptions requestOptions) {
            return scripted.sendRequest(request, requestOptions);
        }
    }
}
