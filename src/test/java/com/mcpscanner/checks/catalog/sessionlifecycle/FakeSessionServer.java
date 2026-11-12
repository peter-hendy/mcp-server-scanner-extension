package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.checks.shared.jsonrpc.JsonRpcEnvelope;
import com.mcpscanner.checks.shared.stream.StreamOpening;
import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.client.ProtocolHttpResponse;
import com.mcpscanner.testutil.FakeMcpServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

final class FakeSessionServer {

    static final String LIVE_SESSION = "live-session-0001";
    static final String TOKEN = "Bearer valid";

    final List<ProtocolHttpRequest> received = new ArrayList<>();
    final List<String> issued = new ArrayList<>();
    final Set<String> deleted = new HashSet<>();
    private final Set<String> open = new HashSet<>(Set.of(LIVE_SESSION));
    Supplier<String> ids = () -> UUID.randomUUID().toString();
    boolean deleteIgnored;
    int deleteStatus = 200;
    int sessionLimit = Integer.MAX_VALUE;
    boolean requiresToken;
    boolean sessionIsCredential;
    boolean acceptsAnySession;
    boolean deletedSessionsAccepted;
    boolean bareSessionsAnswerWithErrors;
    boolean probeOperationsFail;
    int initializedStatus;
    int unissuedIdStatus;
    int malformedIdStatus;
    int sessionlessStatus;
    java.util.function.Predicate<String> statelessFormat;
    int throttleAfterSessions = Integer.MAX_VALUE;
    boolean deletedStreamsOpen;
    boolean streamsOpenForAnyId;
    int streamRefusalStatus = 404;
    final List<HttpRequest> streamRequests = new ArrayList<>();

    synchronized StreamOpening stream(HttpRequest request) {
        streamRequests.add(request);
        String session = request.headerValue("Mcp-Session-Id");
        boolean opens = streamsOpenForAnyId || (session != null && (open.contains(session)
                || deletedStreamsOpen && deleted.contains(session)));
        HttpResponse response = opens
                ? HttpResponse.httpResponse("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n\r\n: open\n\n")
                : HttpResponse.httpResponse("HTTP/1.1 " + streamRefusalStatus + " Refused\r\n\r\n");
        return new StreamOpening(HttpRequestResponse.httpRequestResponse(request, response), opens);
    }

    synchronized Optional<ProtocolHttpResponse> respond(ProtocolHttpRequest request) {
        received.add(request);
        String session = header(request, "Mcp-Session-Id");
        if (request.method().equals("DELETE")) {
            return Optional.of(delete(request, session));
        }
        if (statelessFormat != null && session != null && !LIVE_SESSION.equals(session)) {
            return Optional.of(statelessFormat.test(session) ? FakeMcpServer.respond(request)
                    : FakeMcpServer.exchange(request, 400, Map.of(), "Malformed session ID"));
        }
        if (sessionlessStatus != 0 && session == null && !"initialize".equals(FakeMcpServer.method(request))) {
            return Optional.of(FakeMcpServer.exchange(request, sessionlessStatus, Map.of(), ""));
        }
        if (malformedIdStatus != 0 && session != null && session.startsWith("invalid-")) {
            return Optional.of(FakeMcpServer.exchange(request, malformedIdStatus, Map.of(), ""));
        }
        if (unissuedIdStatus != 0 && neverIssued(session)) {
            return Optional.of(FakeMcpServer.exchange(request, unissuedIdStatus, Map.of(), ""));
        }
        if (initializedStatus != 0 && "notifications/initialized".equals(FakeMcpServer.method(request))) {
            return Optional.of(FakeMcpServer.exchange(request, initializedStatus, Map.of(), ""));
        }
        boolean tokenless = !TOKEN.equals(header(request, "Authorization"));
        if (requiresToken && tokenless && bareSessionsAnswerWithErrors && session != null && open.contains(session)) {
            return Optional.of(jsonRpcError(request));
        }
        if (requiresToken && tokenless && !sessionAuthenticates(session)) {
            return Optional.of(FakeMcpServer.exchange(request, 401, Map.of("WWW-Authenticate", List.of("Bearer")), ""));
        }
        String method = FakeMcpServer.method(request);
        if ("initialize".equals(method)) {
            return Optional.of(initialize(request));
        }
        if (deletedSessionsAccepted && deleted.contains(session)) {
            return Optional.of(FakeMcpServer.exchange(request, 202, Map.of(), ""));
        }
        if (session == null || !known(session)) {
            return Optional.of(FakeMcpServer.exchange(request, 404, Map.of(), "Session not found"));
        }
        if (probeOperationsFail && !LIVE_SESSION.equals(session)) {
            return Optional.of(jsonRpcError(request));
        }
        return Optional.of(FakeMcpServer.respond(request));
    }

    void endLiveSession() {
        open.remove(LIVE_SESSION);
    }

    private static ProtocolHttpResponse jsonRpcError(ProtocolHttpRequest request) {
        String id = JsonRpcEnvelope.of(request.body()).id().map(Object::toString).orElse("null");
        return FakeMcpServer.exchange(request, 200, Map.of("Content-Type", List.of("application/json")),
                "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"error\":{\"code\":-32603,\"message\":\"failed\"}}");
    }

    private boolean sessionAuthenticates(String session) {
        return sessionIsCredential && session != null && known(session);
    }

    boolean neverIssued(String session) {
        return session != null && !issued.contains(session) && !LIVE_SESSION.equals(session);
    }

    private boolean known(String session) {
        return acceptsAnySession || open.contains(session);
    }

    long deletesOf(String session) {
        return received.stream().filter(request -> request.method().equals("DELETE")
                && session.equals(header(request, "Mcp-Session-Id"))).count();
    }

    private ProtocolHttpResponse initialize(ProtocolHttpRequest request) {
        if (issued.size() >= throttleAfterSessions) {
            return FakeMcpServer.exchange(request, 429, Map.of("Retry-After", List.of("60")), "");
        }
        if (issued.size() >= sessionLimit) {
            return FakeMcpServer.exchange(request, 503, Map.of(), "Too many sessions");
        }
        String id = ids.get();
        if (id == null) {
            return FakeMcpServer.exchange(request, 200, Map.of("Content-Type", List.of("application/json")),
                    FakeMcpServer.initializeReply(request));
        }
        issued.add(id);
        open.add(id);
        return FakeMcpServer.exchange(request, 200, Map.of("Content-Type", List.of("application/json"),
                "Mcp-Session-Id", List.of(id)), FakeMcpServer.initializeReply(request));
    }

    private ProtocolHttpResponse delete(ProtocolHttpRequest request, String session) {
        if (deleteStatus != 200) {
            return FakeMcpServer.exchange(request, deleteStatus, Map.of(), "");
        }
        deleted.add(session);
        if (!deleteIgnored) {
            open.remove(session);
        }
        return FakeMcpServer.exchange(request, 200, Map.of(), "");
    }

    static String header(ProtocolHttpRequest request, String name) {
        return request.headers().entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }
}
