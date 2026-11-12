package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import com.mcpscanner.checks.shared.oauth.ClientMetadata;

import java.util.List;

final class ProbeClientMetadata {

    private static final String CLIENT_NAME = "Burp MCP redirect policy probe";

    private ProbeClientMetadata() {
    }

    static ClientMetadata redirectingTo(List<String> redirectUris) {
        return ClientMetadata.codeGrant(CLIENT_NAME, redirectUris);
    }
}
