package com.mcpscanner.cvetargets.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.cvetargets.manifest.SeedRequest;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.SseResponseParser;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Optional;

public final class McpCallAttempt implements ReadinessAttempt {

    private static final String INITIALIZED = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
    private static final int REASON_LIMIT = 300;

    private final ReadinessRequests requests = new ReadinessRequests();
    private final String call;

    public McpCallAttempt(SeedRequest call) {
        ObjectNode envelope = McpObjectMapper.INSTANCE.createObjectNode().put("jsonrpc", "2.0").put("id", 1)
                .put("method", call.method());
        envelope.set("params", call.params());
        this.call = envelope.toString();
    }

    @Override
    public Optional<String> notReadyBecause(ArmEndpoint endpoint) {
        try {
            Optional<String> session = openSession(endpoint);
            HttpResponse<String> response = requests.post(endpoint, call, session);
            return succeeded(response) ? Optional.empty() : Optional.of(reason(response));
        } catch (IOException e) {
            return Optional.of(e.getClass().getSimpleName());
        }
    }

    private Optional<String> openSession(ArmEndpoint endpoint) throws IOException {
        Optional<String> session = requests.post(endpoint, McpInitializeAttempt.INITIALIZE, Optional.empty())
                .headers().firstValue(ReadinessRequests.SESSION_HEADER);
        if (session.isPresent()) {
            requests.post(endpoint, INITIALIZED, session);
        }
        return session;
    }

    private static boolean succeeded(HttpResponse<String> response) {
        JsonNode result = reply(response).path("result");
        return response.statusCode() / 100 == 2 && result.isObject() && !result.path("isError").asBoolean(false);
    }

    private static JsonNode reply(HttpResponse<String> response) {
        String body = SseResponseParser.isEventStream(response.headers().firstValue("Content-Type").orElse(null))
                ? SseResponseParser.extractJsonRpcResponse(response.body()) : response.body();
        try {
            return body == null ? McpObjectMapper.INSTANCE.missingNode() : McpObjectMapper.INSTANCE.readTree(body);
        } catch (IOException unreadable) {
            return McpObjectMapper.INSTANCE.missingNode();
        }
    }

    private static String reason(HttpResponse<String> response) {
        String body = response.body();
        return "authenticated call not answered: status " + response.statusCode() + ", "
                + (body.length() > REASON_LIMIT ? body.substring(0, REASON_LIMIT) : body);
    }
}
