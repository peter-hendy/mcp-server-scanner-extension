package com.mcpscanner.core.connection;

import com.mcpscanner.core.oauth.OAuthCredential;
import java.util.Optional;
import java.util.Set;

public interface OAuthCredentialAccess {
    Optional<OAuthCredential> current();
    boolean refreshAvailable();
    void refresh();
    void authorize(Set<String> scopes);
}
