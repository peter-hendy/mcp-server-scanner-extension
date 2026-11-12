package com.mcpscanner.proxy;

import burp.api.montoya.http.message.responses.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.IOException;
import java.util.Set;

public final class ProxyFabricatedReply {

    private static final ObjectMapper JSON = McpObjectMapper.INSTANCE;
    private static final Set<Integer> PROXY_FAULT_STATUSES = Set.of(409, 411, 413, 502, 503);

    private ProxyFabricatedReply() {
    }

    public static boolean matches(HttpResponse response) {
        return PROXY_FAULT_STATUSES.contains((int) response.statusCode()) && isBareErrorObject(response.bodyToString());
    }

    private static boolean isBareErrorObject(String body) {
        try {
            JsonNode json = JSON.readTree(body);
            return json != null && json.isObject() && json.size() == 1 && json.path("error").isTextual();
        } catch (IOException notJson) {
            return false;
        }
    }
}
