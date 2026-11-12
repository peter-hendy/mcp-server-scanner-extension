package com.mcpscanner.mcp;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

public final class McpRequestDetector {

    private McpRequestDetector() {}

    public static McpRequestKind classify(HttpRequestResponse requestResponse) {
        return JsonRpcRequestClassifier.classify(requestResponse);
    }

    public static boolean isNotification(HttpRequest request) {
        return isNotificationBody(request.bodyToString());
    }

    public static boolean isNotificationBody(String body) {
        return singleRequest(body).filter(root -> !root.has("id")).isPresent();
    }

    public static boolean isInitializeBody(String body) {
        return singleRequest(body).filter(root -> McpMethods.INITIALIZE.equals(root.path("method").asText()))
                .isPresent();
    }

    public static boolean isToolCallBody(String body) {
        return singleRequest(body).filter(root -> root.has("id")
                && McpMethods.TOOLS_CALL.equals(root.path("method").asText())).isPresent();
    }

    private static Optional<JsonNode> singleRequest(String body) {
        try {
            JsonNode root = McpObjectMapper.INSTANCE.readTree(body);
            return root != null && root.isObject() && root.path("method").isTextual()
                    ? Optional.of(root) : Optional.empty();
        } catch (Exception unparsable) {
            return Optional.empty();
        }
    }

    public static String extractBaseUrl(HttpRequestResponse requestResponse) {
        return baseUrl(requestResponse.request().httpService());
    }

    public static String jsonRpcBody(HttpResponse response) {
        String body = response.bodyToString();
        if (!isEventStream(response)) {
            return body;
        }
        String unwrapped = SseResponseParser.extractJsonRpcResponse(body);
        return unwrapped != null ? unwrapped : body;
    }

    private static boolean isEventStream(HttpResponse response) {
        String contentType = response.headerValue("Content-Type");
        return contentType != null && contentType.toLowerCase().contains("text/event-stream");
    }

    private static String baseUrl(HttpService service) {
        if (service == null) {
            return "";
        }
        return HttpOrigin.of(service).baseUrl();
    }
}
