package com.mcpscanner.scan.request;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Map;

public interface ScanMessageRenderer {

    HttpRequest renderJsonRpc(String mcpEndpoint, McpMessage message, Map<String, String> sessionHeaders);

    HttpRequest renderClientRegistration(ClientRegistrationMessage message);
}
