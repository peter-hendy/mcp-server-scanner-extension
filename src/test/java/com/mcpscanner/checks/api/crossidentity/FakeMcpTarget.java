package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.testutil.ImmutableHttpRequest;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class FakeMcpTarget {

    static final String ENDPOINT = "https://example.test/mcp";

    private final Function<HttpRequest, FakeReply> server;
    private final List<HttpRequest> sent = new CopyOnWriteArrayList<>();

    public FakeMcpTarget(Function<HttpRequest, FakeReply> server) {
        this.server = server;
    }

    public Http http() {
        Http http = mock(Http.class);
        when(http.sendRequest(any(HttpRequest.class))).thenAnswer(invocation -> exchange(invocation.getArgument(0)));
        return http;
    }

    public HttpRequestResponse exchange(HttpRequest request) {
        sent.add(request);
        return HttpRequestResponse.httpRequestResponse(request, HttpResponse.httpResponse(server.apply(request).raw()));
    }

    public List<HttpRequest> sent() {
        return List.copyOf(sent);
    }

    public static HttpRequest toolCall(String tool, String arguments, String principal) {
        return request("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"" + tool
                + "\",\"arguments\":" + arguments + "}}", principal);
    }

    public static HttpRequest resourceRead(String uri, String principal) {
        return request("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"resources/read\",\"params\":{\"uri\":\""
                + uri + "\"}}", principal);
    }

    public static HttpRequest request(String body, String principal) {
        Map<String, String> headers = new LinkedHashMap<>(Map.of("Content-Type", "application/json"));
        if (principal != null) {
            headers.put("Authorization", "Bearer " + principal);
        }
        return ImmutableHttpRequest.post(ENDPOINT, headers, body);
    }

    public static String principal(HttpRequest request) {
        String authorization = request.headerValue("Authorization");
        if (authorization != null) {
            return authorization.replace("Bearer ", "");
        }
        String key = request.headerValue("X-Tenant-Key");
        return key == null ? null : key.replace("key-", "");
    }

    public static String method(HttpRequest request) {
        return body(request).path("method").asText();
    }

    public static String tool(HttpRequest request) {
        return body(request).path("params").path("name").asText();
    }

    public static String uri(HttpRequest request) {
        return body(request).path("params").path("uri").asText();
    }

    public static String argument(HttpRequest request, String name) {
        JsonNode value = body(request).path("params").path("arguments").path(name);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    public static JsonNode arguments(HttpRequest request) {
        return body(request).path("params").path("arguments");
    }

    public static String session(HttpRequest request) {
        return request.headerValue("Mcp-Session-Id");
    }

    private static JsonNode body(HttpRequest request) {
        try {
            return McpObjectMapper.INSTANCE.readTree(request.bodyToString());
        } catch (Exception notJson) {
            return McpObjectMapper.INSTANCE.createObjectNode();
        }
    }
}
