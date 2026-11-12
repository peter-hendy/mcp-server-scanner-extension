package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.ImmutableHttpRequest;

import java.util.LinkedHashMap;
import java.util.Map;

final class Seeds {

    static final String CANARY = "c4n4ry";
    static final String LIVE_SESSION = "live-session-123";
    static final String TOOL_CALL = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\","
            + "\"params\":{\"name\":\"read_secret\",\"arguments\":{\"path\":\"/etc\"},\"_meta\":{\"k\":1}}}";

    private Seeds() {}

    static HttpRequest authenticated(String url) {
        return authenticated(url, TOOL_CALL);
    }

    static HttpRequest authenticated(String url, String body) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json, text/event-stream");
        headers.put("Authorization", "Bearer real-token");
        headers.put("Cookie", "sid=abc");
        headers.put("X-Api-Key", "real-key");
        headers.put("Mcp-Session-Id", LIVE_SESSION);
        headers.put("MCP-Protocol-Version", "2025-06-18");
        return ImmutableHttpRequest.post(url, headers, body);
    }
}
