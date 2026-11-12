package com.mcpscanner.cvetargets.oauth;

import java.net.URI;
import java.util.List;

final class AuthorizationServerLocations {

    private AuthorizationServerLocations() {
    }

    static List<URI> of(URI issuer) {
        String origin = issuer.getScheme() + "://" + issuer.getRawAuthority();
        String path = issuer.getRawPath() == null ? "" : issuer.getRawPath().replaceAll("/$", "");
        return List.of(
                URI.create(origin + "/.well-known/oauth-authorization-server" + path),
                URI.create(origin + path + "/.well-known/oauth-authorization-server"),
                URI.create(origin + path + "/.well-known/openid-configuration"));
    }
}
