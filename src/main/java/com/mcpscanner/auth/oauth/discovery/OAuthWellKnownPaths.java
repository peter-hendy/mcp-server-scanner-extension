package com.mcpscanner.auth.oauth.discovery;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.List;
import java.util.stream.Stream;

public final class OAuthWellKnownPaths {
    public static final String AS_WELL_KNOWN_PATH = "/.well-known/oauth-authorization-server";
    public static final String OIDC_WELL_KNOWN_PATH = "/.well-known/openid-configuration";
    public static final String PRM_WELL_KNOWN_PATH = "/.well-known/oauth-protected-resource";

    private OAuthWellKnownPaths() {}

    public static Optional<URI> protectedResourceMetadataUrl(URI mcpResource) {
        try {
            return Optional.of(new URI(mcpResource.getScheme(), null, mcpResource.getHost(),
                    mcpResource.getPort(), PRM_WELL_KNOWN_PATH, null, null));
        } catch (URISyntaxException invalidResource) {
            return Optional.empty();
        }
    }

    public static List<URI> authorizationServerMetadataUrls(URI issuer) {
        String origin = issuer.getScheme() + "://" + issuer.getRawAuthority();
        String path = issuer.getRawPath() != null ? issuer.getRawPath() : "";
        if (path.equals("/")) {
            path = "";
        }
        String appendedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return Stream.of(origin + AS_WELL_KNOWN_PATH + path,
                        origin + OIDC_WELL_KNOWN_PATH + path,
                        origin + appendedPath + OIDC_WELL_KNOWN_PATH)
                .map(URI::create).distinct().toList();
    }
}
