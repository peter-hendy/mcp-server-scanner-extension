package com.mcpscanner.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.IOException;
import java.util.regex.Pattern;

public final class AuthorisationRefusal {

    private static final Pattern REFUSAL_WORDING = Pattern.compile(
            "unauthori[sz]ed|unauthenticated|not authenticated|authentication required",
            Pattern.CASE_INSENSITIVE);

    private AuthorisationRefusal() {
    }

    public static boolean refuses(int status, String body) {
        return status == 401 || status == 403 || refusesInBody(body);
    }

    private static boolean refusesInBody(String body) {
        try {
            JsonNode reply = McpObjectMapper.INSTANCE.readTree(body);
            return reply != null && (refusedError(reply.path("error")) || refusedToolResult(reply.path("result")));
        } catch (IOException | RuntimeException unreadable) {
            return false;
        }
    }

    private static boolean refusedError(JsonNode error) {
        return error.isObject() && REFUSAL_WORDING.matcher(error.path("message").asText()).find();
    }

    private static boolean refusedToolResult(JsonNode result) {
        return result.path("isError").asBoolean(false)
                && REFUSAL_WORDING.matcher(result.path("content").toString()).find();
    }
}
