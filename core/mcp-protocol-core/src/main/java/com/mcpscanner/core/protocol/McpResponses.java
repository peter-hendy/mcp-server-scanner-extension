package com.mcpscanner.core.protocol;

public final class McpResponses {
    public record Reply(long id, java.util.Map<String, Object> result, java.util.Map<String, Object> error) {}
    public record GenericReply(McpRequestId id, java.util.Map<String, Object> result, java.util.Map<String, Object> error) {
        public Reply numeric() {
            if (!(id.value() instanceof Long value)) throw new IllegalArgumentException("numeric reply ID required");
            return new Reply(value, result, error);
        }
    }
    public record Session(String version, String sessionId) {}
    public record ServerRequest(McpRequestId id, String method) {}

    private McpResponses() {}

    public static Reply parse(String body, int maxBytes) {
        return parseGeneric(body, maxBytes).numeric();
    }

    public static GenericReply parseGeneric(String body, int maxBytes) {
        java.util.Map<String, Object> object = object(JsonCodec.parse(body, maxBytes));
        if (!"2.0".equals(object.get("jsonrpc"))) throw new IllegalArgumentException("invalid JSON-RPC version");
        var id = McpRequestId.of(object.get("id"));
        boolean result = object.get("result") != null;
        boolean error = object.get("error") != null;
        if (result == error) throw new IllegalArgumentException("JSON-RPC response requires exactly one non-null result or error");
        return new GenericReply(id, result ? object(object.get("result")) : null,
            error ? object(object.get("error")) : null);
    }

    /**
     * Like {@link #parseGeneric}, but also accepts a JSON-RPC 2.0 error response whose request ID
     * could not be determined ({@code "id": null}); its {@link GenericReply#id()} is then null.
     */
    public static GenericReply parseGenericAllowingNullErrorId(String body, int maxBytes) {
        try {
            return parseGeneric(body, maxBytes);
        } catch (IllegalArgumentException strict) {
            java.util.Map<String, Object> object = object(JsonCodec.parse(body, maxBytes));
            if (!"2.0".equals(object.get("jsonrpc")) || !object.containsKey("id") || object.get("id") != null
                || object.get("result") != null || !(object.get("error") instanceof java.util.Map<?, ?>)) throw strict;
            return new GenericReply(null, null, object(object.get("error")));
        }
    }

    public static Session negotiate(Reply reply, String offeredVersion, java.util.Map<String, String> headers) {
        requireInitializeResult(reply, offeredVersion);
        Object versionValue = reply.result().get("protocolVersion");
        if (!(versionValue instanceof String version) || !McpRequests.supportedHandshake(version))
            throw new IllegalArgumentException("server answered protocolVersion " + versionValue + " to offered "
                + offeredVersion + "; supported versions are " + HandshakeVersions.describe());
        String sessionId = header(headers, "MCP-Session-Id");
        if (sessionId != null && !headerSafe(sessionId)) throw new IllegalArgumentException("invalid session ID");
        return new Session(version, sessionId);
    }

    private static void requireInitializeResult(Reply reply, String offeredVersion) {
        if (!McpRequests.supportedHandshake(offeredVersion))
            throw new IllegalArgumentException("offered protocolVersion " + offeredVersion
                + " is not a supported initialize version");
        String stage = "initialize offering protocolVersion " + offeredVersion;
        if (reply == null) throw new IllegalArgumentException(stage + " received no reply");
        if (reply.id() != 1) throw new IllegalArgumentException(stage + " received reply ID " + reply.id() + " instead of 1");
        if (reply.result() == null)
            throw new IllegalArgumentException(stage + " returned a JSON-RPC error: "
                + (reply.error() == null ? "none" : reply.error().get("message")));
    }

    private static final String UNSUPPORTED_VERSION_MESSAGE = "Unsupported protocol version: ";

    public static String counterOffer(String body, int maxBytes) {
        try {
            String offered = counterOffer(parse(body, maxBytes));
            if (offered != null) return offered;
        } catch (IllegalArgumentException invalid) {}
        try {
            java.util.Map<String, Object> reply = object(JsonCodec.parse(body, maxBytes));
            if (!"2.0".equals(reply.get("jsonrpc"))) return null;
            java.util.Map<String, Object> error = object(reply.get("error"));
            Object code = error.get("code");
            boolean invalidRequest = code instanceof java.math.BigDecimal number
                && number.compareTo(java.math.BigDecimal.valueOf(-32600)) == 0;
            return invalidRequest && error.get("message") instanceof String message
                && message.startsWith(UNSUPPORTED_VERSION_MESSAGE)
                ? HandshakeVersions.olderThan(message.substring(UNSUPPORTED_VERSION_MESSAGE.length())) : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public static String counterOffer(Reply reply) {
        if (reply == null || reply.id() != 1 || reply.error() == null) return null;
        Object code = reply.error().get("code");
        if (!(code instanceof java.math.BigDecimal number) || number.compareTo(java.math.BigDecimal.valueOf(-32602)) != 0)
            return null;
        Object data = reply.error().get("data");
        if (!(data instanceof java.util.Map<?, ?> map)) return null;
        java.util.List<Object> candidates = new java.util.ArrayList<>();
        for (String field : java.util.List.of("supportedVersions", "supported"))
            if (map.get(field) instanceof java.util.List<?> versions) candidates.addAll(versions);
        return HandshakeVersions.newestOf(candidates);
    }

    public static boolean modernDiscovery(Reply reply) {
        if (reply == null || reply.id() != 1 || reply.result() == null) return false;
        Object capabilities = reply.result().get("capabilities");
        Object versions = reply.result().get("supportedVersions");
        return capabilities instanceof java.util.Map<?, ?> && versions instanceof java.util.List<?> list
            && list.contains(McpRequests.MODERN_VERSION);
    }

    public static boolean modernCounterOffer(Reply reply) {
        if (reply == null || reply.id() != 1 || reply.error() == null) return false;
        Object code = reply.error().get("code");
        if (!(code instanceof java.math.BigDecimal number)
            || number.compareTo(java.math.BigDecimal.valueOf(-32022)) != 0) return false;
        Object data = reply.error().get("data");
        return data instanceof java.util.Map<?, ?> map && map.get("supportedVersions") instanceof java.util.List<?> versions
            && versions.contains(McpRequests.MODERN_VERSION);
    }

    private static String header(java.util.Map<String, String> headers, String name) {
        if (headers == null) return null;
        for (java.util.Map.Entry<String, String> entry : headers.entrySet())
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        return null;
    }

    private static boolean headerSafe(String value) {
        if (value.isBlank()) return false;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < 33 || value.charAt(i) > 126) return false;
        return true;
    }

    private static java.util.Map<String, Object> object(Object value) {
        if (!(value instanceof java.util.Map<?, ?> map)) throw new IllegalArgumentException("JSON object required");
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("object key required");
            values.put(key, entry.getValue());
        }
        return java.util.Collections.unmodifiableMap(values);
    }
}
