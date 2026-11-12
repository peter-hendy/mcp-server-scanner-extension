package consumer;

import com.mcpscanner.core.connection.*;
import com.mcpscanner.core.oauth.*;
import com.mcpscanner.core.protocol.*;
import java.net.URI;
import java.time.Instant;
import java.util.*;

public final class ExternalConsumerTest {
    public static void main(String[] args) {
        var binding = new CredentialBinding(URI.create("https://issuer.example"), URI.create("https://mcp.example/mcp"), "client", Set.of());
        var access = new OAuthCredentialAccess() {
            public Optional<OAuthCredential> current() {
                return Optional.of(new OAuthCredential(binding, "fixture", null, Instant.MAX, Set.of()));
            }
            public boolean refreshAvailable() { return false; }
            public void refresh() { throw new AssertionError(); }
            public void authorize(Set<String> scopes) { throw new AssertionError(); }
        };
        var registry = new CredentialProviderRegistry().with("oauth", new OAuthCredentialProvider(binding, access, scopes -> AuthorizationApproval.DENIED));
        var connection = new McpConnection(new McpFlow(8192, 10), registry.resolve("oauth"));
        var session = connection.openHttpSession(request -> {
            if (!"Bearer fixture".equals(request.headers().get("Authorization"))) throw new AssertionError("Missing authentication");
            return new McpFlow.HttpResponse(200, Map.of(),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{},\"supportedVersions\":[\"2026-07-28\"]}}", true);
        }, "consumer", "1");
        if (!McpRequests.MODERN_VERSION.equals(session.session().version())) throw new AssertionError("Negotiation failed");
        var legacy = connection.openInitializeHttpSession(request -> {
            if (!"Bearer fixture".equals(request.headers().get("Authorization"))) throw new AssertionError("Missing authentication");
            if (request.body().contains("notifications/initialized"))
                return new McpFlow.HttpResponse(202, Map.of(), "", true);
            if (!request.body().contains("\"initialize\"")) throw new AssertionError("Expected fixed legacy initialize");
            return new McpFlow.HttpResponse(200, Map.of("MCP-Session-Id", "consumer-session"),
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"}}", true);
        }, "consumer", "1", "2025-06-18");
        if (!"2025-06-18".equals(legacy.session().version())) throw new AssertionError("Legacy negotiation failed");
        if (!"consumer-session".equals(legacy.session().sessionId())) throw new AssertionError("Legacy session lost");
        textualSseId(connection);
        probeSession(connection);
        if (!Optional.of("live").equals(SseSessionIds.from("/messages/?session_id=live")))
            throw new AssertionError("SSE session ID rule not public");
        System.out.println("ExternalConsumerTest passed using binary JAR dependencies");
    }
    private static void probeSession(McpConnection connection) {
        var deleted = new ArrayList<String>();
        try (var probe = connection.openInitializeHttpProbe(request -> request.body().contains("\"initialize\"")
                ? new McpFlow.HttpResponse(200, Map.of("MCP-Session-Id", "probe-session"),
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"}}", true)
                : new McpFlow.HttpResponse(202, Map.of(), "", true),
            headers -> {
                deleted.add(headers.get("MCP-Session-Id"));
                return new McpFlow.HttpResponse(204, Map.of(), "", true);
            }, "consumer", "1", "2025-06-18")) {
            if (!Optional.of("probe-session").equals(probe.sessionId())) throw new AssertionError("Probe session ID lost");
        }
        if (!List.of("probe-session").equals(deleted)) throw new AssertionError("Probe session not terminated");
    }

    private static void textualSseId(McpConnection connection) {
        var id = McpRequestId.of("scan-\"\\\nπ");
        SseStreamFactory factory = headers -> {
            var events = new ArrayDeque<String>();
            events.add("event: endpoint\ndata: /message\n\n");
            events.add("data: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2024-11-05\"}}\n\n");
            return new SseStreamFactory.Stream() {
                public String nextEvent() { return events.poll(); }
                public McpFlow.HttpResponse post(String endpoint, McpRequests.Request request) {
                    if (request.body().contains("tools/list")) {
                        var body = (Map<?, ?>) JsonDocuments.parse(request.body(), 8192);
                        if (!id.equals(McpRequestId.of(body.get("id")))) throw new AssertionError("wire ID changed");
                        events.add("data: {\"jsonrpc\":\"2.0\",\"id\":17,\"result\":{}}\n\n");
                        events.add("data: {\"jsonrpc\":\"2.0\",\"id\":" + id.json() + ",\"result\":{}}\n\n");
                    }
                    return new McpFlow.HttpResponse(202, Map.of(), "", true);
                }
                public void close() {}
            };
        };
        try (var session = connection.openSse(factory, "https://mcp.example/sse", "consumer", "1")) {
            if (!session.call(session.session().operation("tools/list", "{}", id), id).id().equals(id))
                throw new AssertionError("matched reply ID changed");
        }
    }
}
