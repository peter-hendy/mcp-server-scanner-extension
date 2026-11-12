package com.mcpscanner.checks.shared.jsonrpc;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public final class JsonRpcEnvelope {

    private final Optional<JsonNode> root;

    private JsonRpcEnvelope(Optional<JsonNode> root) {
        this.root = root;
    }

    public static JsonRpcEnvelope of(HttpRequest request) {
        return request == null ? new JsonRpcEnvelope(Optional.empty()) : of(request.bodyToString());
    }

    public static JsonRpcEnvelope of(String body) {
        return new JsonRpcEnvelope(JsonRpcMessages.parsed(body));
    }

    public static JsonRpcEnvelope of(byte[] body) {
        return new JsonRpcEnvelope(JsonRpcMessages.parsed(body));
    }

    public Optional<JsonNode> root() {
        return root;
    }

    public boolean singleMessage() {
        return message().isPresent();
    }

    public boolean isJsonRpc2() {
        return message().filter(JsonRpcMessages::isJsonRpc2).isPresent();
    }

    public boolean declaresJsonRpcVersion() {
        return message().filter(message -> message.path("jsonrpc").isTextual()).isPresent();
    }

    public Optional<String> method() {
        return message().flatMap(message -> text(message.path("method")));
    }

    public Optional<JsonNode> id() {
        return message().map(message -> message.get("id")).filter(id -> !id.isNull());
    }

    public boolean hasScalarId() {
        return id().filter(id -> id.isTextual() || id.isNumber()).isPresent();
    }

    public boolean notification() {
        return singleMessage() && id().isEmpty();
    }

    public Optional<String> paramsName() {
        return message().flatMap(message -> text(message.path("params").path("name")));
    }

    public Optional<String> paramsUri() {
        return message().flatMap(message -> text(message.path("params").path("uri")));
    }

    public boolean isAnsweredBy(JsonNode message) {
        return id().filter(expected -> idMatches(message.get("id"), expected)).isPresent();
    }

    private Optional<JsonNode> message() {
        return root.filter(JsonNode::isObject);
    }

    private static Optional<String> text(JsonNode node) {
        return node.isTextual() ? Optional.of(node.asText()) : Optional.empty();
    }

    private static boolean idMatches(JsonNode actual, JsonNode expected) {
        if (actual == null) {
            return false;
        }
        if (actual.isNumber() && expected.isNumber()) {
            return actual.decimalValue().compareTo(expected.decimalValue()) == 0;
        }
        return actual.isTextual() && actual.equals(expected);
    }
}
