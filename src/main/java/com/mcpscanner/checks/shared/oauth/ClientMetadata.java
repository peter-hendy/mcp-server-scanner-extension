package com.mcpscanner.checks.shared.oauth;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;

import java.util.List;

public record ClientMetadata(String clientName, List<String> redirectUris, List<String> grantTypes) {

    private static final String CODE_GRANT = "authorization_code";
    private static final String REFRESH_GRANT = "refresh_token";

    public ClientMetadata {
        redirectUris = List.copyOf(redirectUris);
        grantTypes = List.copyOf(grantTypes);
    }

    public static ClientMetadata codeGrant(String clientName, List<String> redirectUris) {
        return new ClientMetadata(clientName, redirectUris, List.of(CODE_GRANT));
    }

    public ClientMetadata withRefreshGrant() {
        return new ClientMetadata(clientName, redirectUris, List.of(CODE_GRANT, REFRESH_GRANT));
    }

    String toJson() {
        ObjectNode body = McpObjectMapper.INSTANCE.createObjectNode().put("client_name", clientName);
        redirectUris.forEach(body.putArray("redirect_uris")::add);
        grantTypes.forEach(body.putArray("grant_types")::add);
        body.putArray("response_types").add("code");
        body.put("token_endpoint_auth_method", "none");
        return body.toString();
    }
}
