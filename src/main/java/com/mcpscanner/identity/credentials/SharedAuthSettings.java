package com.mcpscanner.identity.credentials;

import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.identity.IdentityAuthKind;

import java.util.Optional;

@FunctionalInterface
public interface SharedAuthSettings {

    Optional<OAuthClientHints> oauthClient();

    default Optional<String> grantBlocker(IdentityAuthKind kind) {
        return Optional.empty();
    }
}
