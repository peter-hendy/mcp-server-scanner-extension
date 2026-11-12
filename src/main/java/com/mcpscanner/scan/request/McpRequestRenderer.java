package com.mcpscanner.scan.request;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Map;

@FunctionalInterface
public interface McpRequestRenderer {

    HttpRequest render(String endpoint, McpMessage message, Map<String, String> sessionHeaders);
}
