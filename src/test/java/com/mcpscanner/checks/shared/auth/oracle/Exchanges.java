package com.mcpscanner.checks.shared.auth.oracle;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.ImmutableHttpRequest;

import java.util.Map;

final class Exchanges {

    static final String TOOL_CALL = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"read_secret\",\"arguments\":{}}}";
    static final String NOTIFICATION = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
    static final String SECRET_RESULT = "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":"
            + "{\"content\":[{\"type\":\"text\",\"text\":\"s3cr3t\"}],\"isError\":false}}";

    private Exchanges() {}

    static HttpRequestResponse toolCall(int status, String body) {
        return exchange(TOOL_CALL, status, "application/json", body);
    }

    static HttpRequestResponse toolCallSse(String body) {
        return exchange(TOOL_CALL, 200, "text/event-stream", body);
    }

    static HttpRequestResponse notification(int status) {
        return exchange(NOTIFICATION, status, "application/json", "");
    }

    static HttpRequestResponse unanswered() {
        return HttpRequestResponse.httpRequestResponse(request(TOOL_CALL), null);
    }

    static HttpRequestResponse exchange(String requestBody, int status, String contentType, String body) {
        HttpResponse response = HttpResponse.httpResponse("HTTP/1.1 " + status + " X\r\nContent-Type: "
                + contentType + "\r\n\r\n" + body);
        return HttpRequestResponse.httpRequestResponse(request(requestBody), response);
    }

    private static burp.api.montoya.http.message.requests.HttpRequest request(String body) {
        return ImmutableHttpRequest.post("https://api.example.com/mcp", Map.of("Content-Type", "application/json"),
                body);
    }
}
