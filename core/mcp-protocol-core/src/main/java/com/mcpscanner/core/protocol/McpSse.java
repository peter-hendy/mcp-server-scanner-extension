package com.mcpscanner.core.protocol;

public final class McpSse {
    private record Event(String name, String data) {}

    private McpSse() {}

    public static String endpoint(String origin, String wire, int maxBytes) {
        Event event = event(wire, maxBytes);
        if (!"endpoint".equals(event.name()) || event.data().isBlank()) throw new IllegalArgumentException("endpoint event required");
        try {
            java.net.URI base = java.net.URI.create(origin);
            java.net.URI target = base.resolve(event.data());
            if (!"http".equalsIgnoreCase(target.getScheme()) && !"https".equalsIgnoreCase(target.getScheme()))
                throw new IllegalArgumentException("HTTP endpoint required");
            if (!base.getScheme().equalsIgnoreCase(target.getScheme()) || base.getHost() == null
                || !base.getHost().equalsIgnoreCase(target.getHost()) || port(base) != port(target)
                || target.getRawUserInfo() != null || target.getRawFragment() != null)
                throw new IllegalArgumentException("cross-origin endpoint");
            String path = target.getRawPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//"))
                throw new IllegalArgumentException("invalid endpoint path");
            return path + (target.getRawQuery() == null ? "" : "?" + target.getRawQuery());
        } catch (NullPointerException invalid) {
            throw new IllegalArgumentException("invalid origin", invalid);
        }
    }

    public static McpResponses.Reply reply(String wire, long expectedId, int maxBytes) {
        var reply = reply(wire, McpRequestId.of(expectedId), maxBytes);
        return reply == null ? null : reply.numeric();
    }

    public static McpResponses.GenericReply reply(String wire, McpRequestId expectedId, int maxBytes) {
        Event event = event(wire, maxBytes);
        java.util.Map<?, ?> message = message(event, maxBytes);
        if (message == null || message.get("method") instanceof String) return null;
        var reply = McpResponses.parseGeneric(event.data(), maxBytes);
        return reply.id().equals(expectedId) ? reply : null;
    }

    public static McpResponses.ServerRequest serverRequest(String wire, int maxBytes) {
        java.util.Map<?, ?> message = message(event(wire, maxBytes), maxBytes);
        if (message == null || !(message.get("method") instanceof String method) || message.get("id") == null) return null;
        try {
            return new McpResponses.ServerRequest(McpRequestId.of(message.get("id")), method);
        } catch (IllegalArgumentException unanswerableId) {
            return null;
        }
    }

    private static java.util.Map<?, ?> message(Event event, int maxBytes) {
        if (!"message".equals(event.name()) || event.data().isBlank()) return null;
        Object value = JsonCodec.parse(event.data(), maxBytes);
        if (!(value instanceof java.util.Map<?, ?> message) || !"2.0".equals(message.get("jsonrpc")))
            throw new IllegalArgumentException("valid JSON-RPC event required");
        return message;
    }

    /** Returns the data of one complete SSE event, whatever its event name. */
    public static String data(String wire, int maxBytes) {
        return event(wire, maxBytes).data();
    }

    public static boolean isEndpoint(String wire, int maxBytes) {
        return "endpoint".equals(event(wire, maxBytes).name());
    }

    private static Event event(String wire, int maxBytes) {
        if (wire == null || maxBytes <= 0 || wire.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > maxBytes)
            throw new IllegalArgumentException("SSE event exceeds limit");
        String normalized = wire.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.endsWith("\n\n"))
            throw new IllegalArgumentException("incomplete SSE event");
        String name = "message";
        StringBuilder data = new StringBuilder();
        for (String line : normalized.split("\n")) {
            if (line.startsWith("event:")) name = line.substring(6).trim();
            else if (line.startsWith("data:")) {
                if (!data.isEmpty()) data.append('\n');
                String value = line.substring(5);
                data.append(value.startsWith(" ") ? value.substring(1) : value);
            }
        }
        return new Event(name, data.toString());
    }

    private static int port(java.net.URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
