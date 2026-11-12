package com.mcpscanner.checks.shared.stream;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.shared.session.SessionHeaders;
import com.mcpscanner.mcp.HeaderMutation;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.core.connection.HttpProbeSession;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StreamGetRequest {

    public static final String EVENT_STREAM = "text/event-stream";

    private static final List<String> MESSAGE_HEADERS = List.of("Content-Type", "Content-Length", "Accept",
            McpHeaders.METHOD, McpHeaders.NAME, McpHeaders.SESSION_ID);

    private StreamGetRequest() {
    }

    public static HttpRequest from(HttpRequest seed, Optional<String> sessionId) {
        HttpRequest get = HeaderMutation.apply(seed.withMethod("GET").withBody(""), MESSAGE_HEADERS,
                Map.of("Accept", EVENT_STREAM));
        return sessionId.map(id -> SessionHeaders.inSession(get, id)).orElse(get);
    }

    public static HttpRequest in(HttpRequest seed, HttpProbeSession session) {
        return from(seed, session.sessionId()).withHeader(McpHeaders.PROTOCOL_VERSION, session.session().version());
    }
}
