package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import com.mcpscanner.checks.shared.oauth.ClientMetadata;
import com.mcpscanner.checks.shared.oauth.ProbeCallback;

import java.net.URI;
import java.util.List;

final class ConsentClientRegistration {

    static final URI REDIRECT_URI = ProbeCallback.loopback("callback");
    static final String CLIENT_NAME_PREFIX = "MCP Scanner ";

    private ConsentClientRegistration() {
    }

    static ClientMetadata carrying(StructureMarker clientNameMarker) {
        return ClientMetadata.codeGrant(CLIENT_NAME_PREFIX + clientNameMarker.payload(),
                List.of(REDIRECT_URI.toString())).withRefreshGrant();
    }
}
