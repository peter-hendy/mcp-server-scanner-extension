package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.JsonStep.Member;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

public final class BurpJsonRpcArguments {

    private static final String JSONRPC_MEMBER = "\"jsonrpc\"";
    private static final Member PARAMS = new Member("params");
    private static final Member META = new Member("_meta");
    private static final Member ARGUMENTS = new Member("arguments");
    private static final Member URI = new Member("uri");

    private final JsonNode call;

    private BurpJsonRpcArguments(JsonNode call) {
        this.call = call;
    }

    public static Optional<BurpJsonRpcArguments> in(byte[] body) {
        if (body == null || !startsLikeObject(body)
                || !new String(body, StandardCharsets.UTF_8).contains(JSONRPC_MEMBER)) {
            return Optional.empty();
        }
        return parse(body).filter(BurpJsonRpcArguments::isCall).map(BurpJsonRpcArguments::new);
    }

    public boolean fuzzesScalarAt(List<JsonStep> steps) {
        if (steps.isEmpty() || !PARAMS.equals(steps.get(0))) {
            return false;
        }
        return withinArguments(withinParams(steps.subList(1, steps.size())));
    }

    private Optional<List<JsonStep>> withinParams(List<JsonStep> path) {
        if (call.get("params").isArray()) {
            return Optional.of(path.isEmpty() ? path : path.subList(1, path.size()));
        }
        return !path.isEmpty() && META.equals(path.get(0)) ? Optional.empty() : Optional.of(path);
    }

    private boolean withinArguments(Optional<List<JsonStep>> path) {
        return path.filter(steps -> switch (call.get("method").asText()) {
            case McpMethods.TOOLS_CALL, McpMethods.PROMPTS_GET -> steps.size() > 1 && ARGUMENTS.equals(steps.get(0));
            case McpMethods.RESOURCES_READ -> steps.equals(List.of(URI));
            default -> true;
        }).isPresent();
    }

    private static boolean startsLikeObject(byte[] body) {
        for (byte b : body) {
            if (b != ' ' && b != '\t' && b != '\r' && b != '\n') {
                return b == '{';
            }
        }
        return false;
    }

    private static Optional<JsonNode> parse(byte[] body) {
        try {
            return Optional.of(McpObjectMapper.INSTANCE.readTree(body));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean isCall(JsonNode value) {
        return value.isObject() && value.path("jsonrpc").isTextual() && "2.0".equals(value.get("jsonrpc").asText())
                && value.path("method").isTextual();
    }
}
