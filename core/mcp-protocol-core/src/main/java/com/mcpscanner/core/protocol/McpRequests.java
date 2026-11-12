package com.mcpscanner.core.protocol;

public final class McpRequests {
    public static final String MODERN_VERSION = "2026-07-28";
    public static final String HANDSHAKE_VERSION = "2025-11-25";
    public static final String SSE_VERSION = "2024-11-05";

    public record Request(String body, java.util.Map<String, String> headers) {}

    private McpRequests() {}

    public static Request modern(String method, String clientName, String clientVersion, String cursor) {
        return modern(method, ClientProfile.of(clientName, clientVersion), cursor);
    }

    public static Request modern(String method, String clientName, String clientVersion, String cursor, long id) {
        return modern(method, ClientProfile.of(clientName, clientVersion), cursor, id);
    }

    public static Request modern(String method, String clientName, String clientVersion, String cursor, McpRequestId id) {
        return modern(method, ClientProfile.of(clientName, clientVersion), cursor, id);
    }

    public static String modernParams(String clientName, String clientVersion, String cursor) {
        return modernParams(ClientProfile.of(clientName, clientVersion), cursor);
    }

    public static Request modernOperation(String method, String clientName, String clientVersion, String params) {
        return modernOperation(method, ClientProfile.of(clientName, clientVersion), params);
    }

    public static Request modernOperation(String method, String clientName, String clientVersion, String params, long id) {
        return modernOperation(method, ClientProfile.of(clientName, clientVersion), params, id);
    }

    public static Request modernOperation(String method, String clientName, String clientVersion, String params, McpRequestId id) {
        return modernOperation(method, ClientProfile.of(clientName, clientVersion), params, id);
    }

    public static Request notification(String method, String params, String version, String sessionId,
                                       String clientName, String clientVersion) {
        return notification(method, params, version, sessionId, ClientProfile.of(clientName, clientVersion));
    }

    public static Request initialize(String version, String clientName, String clientVersion) {
        return initialize(version, ClientProfile.of(clientName, clientVersion));
    }

    public static String initializeParams(String version, String clientName, String clientVersion) {
        return initializeParams(version, ClientProfile.of(clientName, clientVersion));
    }

    public static Request modern(String method, ClientProfile client, String cursor) {
        return modern(method, client, cursor, 1);
    }

    public static Request modern(String method, ClientProfile client, String cursor, long id) {
        return modern(method, client, cursor, McpRequestId.of(id));
    }

    public static Request modern(String method, ClientProfile client, String cursor, McpRequestId id) {
        if (method == null || method.isBlank()) throw new IllegalArgumentException("method required");
        if (method.equals("tools/call") || method.equals("prompts/get") || method.equals("resources/read"))
            throw new IllegalArgumentException("named operation requires modernOperation");
        return new Request(envelope(method, modernParams(client, cursor), id), modernHeaders(method));
    }

    public static String modernParams(ClientProfile client, String cursor) {
        return "{\"_meta\":" + modernMetadata(client)
            + (cursor == null ? "" : ",\"cursor\":" + quote(cursor)) + "}";
    }

    public static Request modernOperation(String method, ClientProfile client, String params) {
        return modernOperation(method, client, params, 1);
    }

    public static Request modernOperation(String method, ClientProfile client, String params, long id) {
        return modernOperation(method, client, params, McpRequestId.of(id));
    }

    public static Request modernOperation(String method, ClientProfile client, String params, McpRequestId id) {
        if (method == null || method.isBlank()) throw new IllegalArgumentException("method required");
        if (!(JsonCodec.parse(params, 1024 * 1024) instanceof java.util.Map<?, ?> values)
            || values.containsKey("_meta")) throw new IllegalArgumentException("params object without _meta required");
        String fields = params.trim();
        fields = fields.substring(1, fields.length() - 1).trim();
        String withMetadata = "{\"_meta\":" + modernMetadata(client)
            + (fields.isEmpty() ? "" : "," + fields) + "}";
        return new Request(envelope(method, withMetadata, id), modernHeaders(method, values));
    }

    public static Request notification(String method, String params, String version, String sessionId,
                                       ClientProfile client) {
        if (method == null || method.isBlank()) throw new IllegalArgumentException("method required");
        if (!(JsonDocuments.parse(params, 1024 * 1024) instanceof java.util.Map<?, ?> values))
            throw new IllegalArgumentException("params object required");
        String framedParams = params;
        java.util.Map<String, String> requestHeaders;
        if (MODERN_VERSION.equals(version)) {
            if (values.containsKey("_meta")) throw new IllegalArgumentException("params without _meta required");
            String fields = params.trim();
            fields = fields.substring(1, fields.length() - 1).trim();
            framedParams = "{\"_meta\":" + modernMetadata(client)
                + (fields.isEmpty() ? "" : "," + fields) + "}";
            requestHeaders = modernHeaders(method, values);
        } else {
            if (!supportedHandshake(version)) throw new IllegalArgumentException("unsupported protocol version");
            requestHeaders = headers(version, sessionId);
        }
        return new Request("{\"jsonrpc\":\"2.0\",\"method\":" + quote(method) + ",\"params\":" + framedParams + "}", requestHeaders);
    }

    public static Request initialize(String version, ClientProfile client) {
        return new Request(envelope("initialize", initializeParams(version, client)), headers(version, null));
    }

    public static String initializeParams(String version, ClientProfile client) {
        if (!supportedHandshake(version)) throw new IllegalArgumentException("unsupported protocol version");
        return "{\"protocolVersion\":" + quote(version) + ",\"capabilities\":" + client.capabilitiesJson()
            + ",\"clientInfo\":" + client.info().json() + "}";
    }

    public static Request initialized(String version, String sessionId) {
        if (!supportedHandshake(version)) throw new IllegalArgumentException("unsupported protocol version");
        return new Request("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", headers(version, sessionId));
    }

    public static Request response(String version, String sessionId, McpRequestId id, String result) {
        if (!MODERN_VERSION.equals(version) && !supportedHandshake(version))
            throw new IllegalArgumentException("unsupported protocol version");
        if (!(JsonCodec.parse(result, 1024 * 1024) instanceof java.util.Map<?, ?>))
            throw new IllegalArgumentException("result object required");
        return new Request("{\"jsonrpc\":\"2.0\",\"id\":" + java.util.Objects.requireNonNull(id).json()
            + ",\"result\":" + result + "}", headers(version, sessionId));
    }

    public static Request legacy(String method, String version, String sessionId, String params) {
        return legacy(method, version, sessionId, params, 1);
    }

    public static Request legacy(String method, String version, String sessionId, String params, long id) {
        if (!supportedLegacy(version)) throw new IllegalArgumentException("unsupported protocol version");
        return httpPost(method, version, sessionId, params, id);
    }

    public static Request httpPost(String method, String version, String sessionId, String params, long id) {
        return httpPost(method, version, sessionId, params, McpRequestId.of(id));
    }

    public static Request httpPost(String method, String version, String sessionId, String params, McpRequestId id) {
        if (!supportedHandshake(version) || MODERN_VERSION.equals(version))
            throw new IllegalArgumentException("unsupported HTTP POST protocol version");
        return new Request(envelope(method, params, id), headers(version, sessionId));
    }

    public static Request sse(String method, String params) {
        return sse(method, params, 1);
    }

    public static Request sse(String method, String params, long id) {
        return sse(method, params, McpRequestId.of(id));
    }

    public static Request sse(String method, String params, McpRequestId id) {
        return new Request(envelope(method, params, id), headers(SSE_VERSION, null));
    }

    public static Request catalog(String method, String version, String sessionId, String cursor) {
        return catalog(method, version, sessionId, cursor, 1);
    }

    public static Request catalog(String method, String version, String sessionId, String cursor, long id) {
        String params = cursor == null ? "{}" : "{\"cursor\":" + quote(cursor) + "}";
        return httpPost(method, version, sessionId, params, id);
    }

    public static boolean supportedLegacy(String version) {
        return supportedHandshake(version) && !SSE_VERSION.equals(version);
    }

    public static boolean supportedHandshake(String version) {
        return HandshakeVersions.supported(version);
    }

    public static java.util.List<String> supportedInitializeVersions() {
        return HandshakeVersions.newestFirst();
    }

    public static String envelope(String method, String params) {
        return envelope(method, params, 1);
    }

    public static String envelope(String method, String params, long id) {
        return envelope(method, params, McpRequestId.of(id));
    }

    public static String envelope(String method, String params, McpRequestId id) {
        if (params == null || params.isBlank()) throw new IllegalArgumentException("params required");
        return "{\"jsonrpc\":\"2.0\",\"id\":" + java.util.Objects.requireNonNull(id).json() + ",\"method\":" + quote(method) + ",\"params\":" + params + "}";
    }

    private static String modernMetadata(ClientProfile client) {
        return "{\"io.modelcontextprotocol/protocolVersion\":" + quote(MODERN_VERSION)
            + ",\"io.modelcontextprotocol/clientInfo\":" + client.info().json()
            + ",\"io.modelcontextprotocol/clientCapabilities\":" + client.capabilitiesJson() + "}";
    }

    private static java.util.Map<String, String> modernHeaders(String method) {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>(headers(MODERN_VERSION, null));
        headers.put("Mcp-Method", method);
        return java.util.Map.copyOf(headers);
    }

    private static java.util.Map<String, String> modernHeaders(String method, java.util.Map<?, ?> params) {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>(modernHeaders(method));
        if (method.equals("tools/call") || method.equals("prompts/get") || method.equals("resources/read")) {
            Object value = params.get(method.equals("resources/read") ? "uri" : "name");
            if (!(value instanceof String name) || name.isEmpty())
                throw new IllegalArgumentException("operation name or URI required");
            headers.put("Mcp-Name", headerName(name));
        }
        return java.util.Map.copyOf(headers);
    }

    private static String headerName(String value) {
        boolean safe = value.equals(value.trim()) && !value.startsWith("=?base64?");
        for (int i = 0; i < value.length(); i++) safe &= value.charAt(i) >= 32 && value.charAt(i) <= 126;
        return safe ? value : "=?base64?" + java.util.Base64.getEncoder().encodeToString(
            value.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "?=";
    }

    private static java.util.Map<String, String> headers(String version, String sessionId) {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json, text/event-stream");
        headers.put("MCP-Protocol-Version", version);
        if (sessionId != null) {
            if (!headerSafe(sessionId)) throw new IllegalArgumentException("invalid session ID");
            headers.put("MCP-Session-Id", sessionId);
        }
        return java.util.Map.copyOf(headers);
    }

    private static boolean headerSafe(String value) {
        if (value.isBlank()) return false;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < 33 || value.charAt(i) > 126) return false;
        return true;
    }

    private static String quote(String value) {
        if (value == null) throw new IllegalArgumentException("string required");
        StringBuilder quoted = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') quoted.append('\\').append(c);
            else if (c < 32) quoted.append(String.format("\\u%04x", (int) c));
            else quoted.append(c);
        }
        return quoted.append('"').toString();
    }
}
