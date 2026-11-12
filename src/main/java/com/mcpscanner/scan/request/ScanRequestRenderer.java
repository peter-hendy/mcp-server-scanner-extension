package com.mcpscanner.scan.request;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Map;

public final class ScanRequestRenderer implements ScanMessageRenderer {

    private final McpRequestRenderer jsonRpcRenderer;
    private final ClientRegistrationRequestFactory registrationFactory;

    public ScanRequestRenderer(McpRequestRenderer jsonRpcRenderer,
                               ClientRegistrationRequestFactory registrationFactory) {
        this.jsonRpcRenderer = jsonRpcRenderer;
        this.registrationFactory = registrationFactory;
    }

    @Override
    public HttpRequest renderJsonRpc(String mcpEndpoint, McpMessage message, Map<String, String> sessionHeaders) {
        return jsonRpcRenderer.render(mcpEndpoint, message, sessionHeaders);
    }

    @Override
    public HttpRequest renderClientRegistration(ClientRegistrationMessage message) {
        return registrationFactory.build(message);
    }
}
