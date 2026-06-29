package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.client.McpScannerSession;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpRequestDetector;
import com.mcpscanner.mcp.McpRequestKind;

/**
 * Burp-edge adapter that turns un-swapped live MCP traffic into {@link ObservedMessage}s and feeds
 * them to the domain {@link McpObservationSink}. The adapter owns everything Burp-specific —
 * classifying the request, parsing the JSON-RPC body, reading the session header — so the sink stays
 * a pure transport-agnostic port.
 *
 * <p>FP-safe: a request that is not MCP, or whose body does not parse as JSON-RPC, produces no sink
 * call and never throws.
 */
public final class BurpObserverAdapter implements BurpTrafficObserver {

    private static final String SESSION_HEADER = "Mcp-Session-Id";

    private final McpObservationSink sink;
    private final McpScannerSession scannerSession;

    public BurpObserverAdapter(McpObservationSink sink, McpScannerSession scannerSession) {
        this.sink = sink;
        this.scannerSession = scannerSession;
    }

    @Override
    public void observeRequest(HttpRequestToBeSent request) {
        if (!isMcpRequest(request)) {
            return;
        }
        // body is parsed twice (here + in McpRequestDetector.classify) on purpose: reuse the canonical detector rather than diverge MCP-detection logic. Revisit only if the live path profiles hot.
        JsonNode parsed = parseJsonRpc(request.bodyToString());
        if (parsed == null) {
            return;
        }
        // transport may be null if a disconnect races an in-flight observe; the row tolerates null.
        TransportType transport = scannerSession.transportType();
        sink.observe(new ObservedMessage(
                Direction.CLIENT_TO_SERVER,
                transport,
                request.headerValue(SESSION_HEADER),
                jsonrpcId(parsed),
                method(parsed),
                request,
                parsed,
                null));
    }

    @Override
    public void observeResponse(HttpResponseReceived response) {
        // TODO(Task 7): build SERVER_TO_CLIENT ObservedMessage from the response and feed the sink.
    }

    private static boolean isMcpRequest(HttpRequestToBeSent request) {
        HttpRequestResponse requestResponse = HttpRequestResponse.httpRequestResponse(request, null);
        return McpRequestDetector.classify(requestResponse) != McpRequestKind.NOT_MCP;
    }

    private static JsonNode parseJsonRpc(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            return McpObjectMapper.INSTANCE.readTree(body);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String method(JsonNode parsed) {
        JsonNode method = parsed.path("method");
        return method.isTextual() ? method.asText() : null;
    }

    private static String jsonrpcId(JsonNode parsed) {
        JsonNode id = parsed.path("id");
        return id.isMissingNode() || id.isNull() ? null : id.asText();
    }
}
