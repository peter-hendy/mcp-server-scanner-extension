package com.mcpscanner.checks.shared.auth.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.checks.shared.jsonrpc.JsonNodes;
import com.mcpscanner.checks.shared.jsonrpc.JsonRpcMessages;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class RefusalText {

    private static final int MAX_DEPTH = 8;
    private static final Pattern REFUSAL = Pattern.compile(
            "(?s)\\s*(?:error\\s*:\\s*)?(?:http\\s*)?(?:(?:401|403)\\s*(?:[: -]|\\[)?\\s*)?"
                    + "(?:unauthorized|forbidden|access denied|access_denied|permission denied|"
                    + "authentication required|authentication failed|unauthenticated|not authenticated|"
                    + "not authorized|bad credentials|login required|invalid token|invalid api key|missing api key|"
                    + "missing credentials|unauthorized access[^\\r\\n]*)"
                    + "(?:\\s*(?::|[-–—])\\s*[^\\r\\n]+)?\\s*\\]?\\s*[.!]?\\s*");
    private static final List<String> STATUS_KEYS = List.of("code", "status", "status_code", "http_status");
    private static final List<String> CONTENT_KEYS = List.of("content", "contents", "messages");

    private RefusalText() {}

    static boolean refusesBody(String body, List<JsonNode> messages) {
        return body != null && REFUSAL.matcher(body.toLowerCase(Locale.ROOT)).matches()
                || messages.stream().anyMatch(RefusalText::refusesMessage);
    }

    private static boolean refusesMessage(JsonNode message) {
        JsonNode result = message.path("result");
        boolean toolError = result.path("isError").asBoolean(false);
        return refuses(message.get("error"))
                || CredentialRequirementText.statedIn(message.get("error"))
                || toolError && (refuses(result) || CredentialRequirementText.statedIn(result.get("content")))
                || refuses(result.get("error"))
                || result.isObject() && refusesResult(result);
    }

    static boolean refusesResult(JsonNode result) {
        return CONTENT_KEYS.stream()
                .map(result::path)
                .filter(JsonNode::isArray)
                .flatMap(entries -> JsonNodes.elements(entries).stream())
                .map(entry -> entry.path("text"))
                .filter(JsonNode::isTextual)
                .anyMatch(text -> refuses(text) || embeddedErrorRefuses(text.asText()));
    }

    static boolean refuses(JsonNode node) {
        return refuses(node, 0);
    }

    private static boolean refuses(JsonNode node, int depth) {
        if (node == null || depth > MAX_DEPTH) {
            return false;
        }
        if (node.isTextual()) {
            return REFUSAL.matcher(node.asText().toLowerCase(Locale.ROOT)).matches();
        }
        if (node.isObject()) {
            return JsonNodes.fields(node).stream().anyMatch(field ->
                    refusalStatus(field.getKey(), field.getValue()) || refuses(field.getValue(), depth + 1));
        }
        return node.isArray() && JsonNodes.elements(node).stream().anyMatch(element -> refuses(element, depth + 1));
    }

    private static boolean embeddedErrorRefuses(String text) {
        return JsonRpcMessages.parsed(text)
                .map(payload -> payload.get("error"))
                .filter(error -> error.isTextual() || error.isObject() || error.isArray())
                .map(RefusalText::refuses)
                .orElse(false);
    }

    private static boolean refusalStatus(String key, JsonNode value) {
        return STATUS_KEYS.contains(key.toLowerCase(Locale.ROOT)) && value.isNumber()
                && (value.asLong() == 401 || value.asLong() == 403);
    }
}
