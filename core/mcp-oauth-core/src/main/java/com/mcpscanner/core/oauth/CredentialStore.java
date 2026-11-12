package com.mcpscanner.core.oauth;

import java.util.Optional;

public interface CredentialStore {
    Optional<OAuthCredential> load(CredentialBinding binding);
    void save(CredentialBinding binding, OAuthCredential credential);
}
