package com.mcpscanner.mcp;

import com.mcpscanner.core.protocol.ClientProfile;
import com.mcpscanner.core.protocol.McpFlow;
import com.mcpscanner.core.protocol.McpRequests;
import com.mcpscanner.core.protocol.McpResponses;
import com.mcpscanner.core.protocol.McpSse;
import com.mcpscanner.core.protocol.PingResponder;
import com.mcpscanner.core.protocol.ServerRequestHandler;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;

public final class ServerPingAnswerer {

    public static final int MAX_REPLIES_PER_CALL = 16;
    private static final ServerPingAnswerer IGNORING =
            new ServerPingAnswerer(ServerRequestHandler.disabled(), reply -> { });

    private final ServerRequestHandler handler;
    private final Consumer<McpRequests.Request> post;
    private int repliesSent;

    private ServerPingAnswerer(ServerRequestHandler handler, Consumer<McpRequests.Request> post) {
        this.handler = handler;
        this.post = post;
    }

    public static ServerPingAnswerer ignoring() {
        return IGNORING;
    }

    static ServerPingAnswerer answering(Map<String, String> sentHeaders, Consumer<McpRequests.Request> post) {
        return new ServerPingAnswerer(new PingResponder(sessionOf(sentHeaders)),
                reply -> post.accept(carryingSentHeaders(sentHeaders, reply)));
    }

    boolean hasBudget() {
        return repliesSent < MAX_REPLIES_PER_CALL;
    }

    public boolean consume(String data) {
        McpResponses.ServerRequest request = serverRequest(data);
        if (request == null) return false;
        reply(request).filter(reply -> repliesSent++ < MAX_REPLIES_PER_CALL).ifPresent(post);
        return true;
    }

    private Optional<McpRequests.Request> reply(McpResponses.ServerRequest request) {
        try {
            return handler.reply(request);
        } catch (IllegalArgumentException unsupportedSession) {
            return Optional.empty();
        }
    }

    private static McpResponses.ServerRequest serverRequest(String data) {
        try {
            return McpSse.serverRequest(asMessageEvent(data), BoundedBodyReader.MAX_RESPONSE_BYTES);
        } catch (IllegalArgumentException notJsonRpc) {
            return null;
        }
    }

    private static String asMessageEvent(String data) {
        return "data: " + data.replace("\n", "\ndata: ") + "\n\n";
    }

    private static McpFlow.Session sessionOf(Map<String, String> sentHeaders) {
        return new McpFlow.Session(header(sentHeaders, McpHeaders.PROTOCOL_VERSION, McpRequests.SSE_VERSION),
                header(sentHeaders, McpHeaders.SESSION_ID, null), null, (ClientProfile) null);
    }

    private static McpRequests.Request carryingSentHeaders(Map<String, String> sentHeaders, McpRequests.Request reply) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sentHeaders.forEach((name, value) -> {
            if (!McpHeaders.isProtocolHeader(name)) headers.put(name, value);
        });
        headers.putAll(reply.headers());
        return new McpRequests.Request(reply.body(), Map.copyOf(headers));
    }

    private static String header(Map<String, String> headers, String name, String fallback) {
        return headers.entrySet().stream()
                .filter(header -> header.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).findFirst().orElse(fallback);
    }
}
