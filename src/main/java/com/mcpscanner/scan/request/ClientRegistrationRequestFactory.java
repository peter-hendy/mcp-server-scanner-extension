package com.mcpscanner.scan.request;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.http.BareRequests;
import com.mcpscanner.http.ConnectionClose;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.mcp.ScannerSentinels;

public final class ClientRegistrationRequestFactory {

    public HttpRequest build(ClientRegistrationMessage message) {
        return ConnectionClose.on(BareRequests.fromUrl(message.registrationEndpoint().toString())
                .withMethod("POST")
                .withHeader("Content-Type", McpHeaders.CONTENT_TYPE_JSON)
                .withHeader("Accept", McpHeaders.CONTENT_TYPE_JSON)
                .withHeader(ScannerSentinels.GENERATED_REGISTRATION_HEADER, ScannerSentinels.SENTINEL_VALUE)
                .withBody(message.metadata().toString()));
    }
}
