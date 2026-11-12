package com.mcpscanner.auth.oauth;

import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;

import java.net.URI;
import java.util.Objects;

public record ExpectedIssuer(URI uri) {

    public ExpectedIssuer {
        Objects.requireNonNull(uri, "issuer");
    }

    public boolean matches(String advertised) {
        return uri.toString().equals(advertised);
    }

    public boolean matches(URI advertised) {
        return advertised != null && matches(advertised.toString());
    }

    public boolean matchesAllowingTrailingSlash(String advertised) {
        return matches(advertised) || (uri + "/").equals(advertised);
    }

    public AuthorizationServerMetadata requireDeclaredBy(AuthorizationServerMetadata metadata) {
        if (metadata.getIssuer() == null || !matches(metadata.getIssuer().getValue())) {
            throw new OAuthException("OAuth metadata issuer mismatch");
        }
        return metadata;
    }
}
