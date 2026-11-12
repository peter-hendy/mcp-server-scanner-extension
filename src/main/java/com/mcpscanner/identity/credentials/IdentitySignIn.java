package com.mcpscanner.identity.credentials;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.identity.Identity;

@FunctionalInterface
public interface IdentitySignIn {

    AuthStrategy signIn(Identity identity, OAuthClientHints hints);
}
