package com.mcpscanner.auth.oauth;

import com.mcpscanner.core.oauth.CredentialBinding;
import com.mcpscanner.core.oauth.CredentialStore;
import com.mcpscanner.core.oauth.OAuthCredential;

import java.util.Optional;

public final class InMemoryCredentialStore implements CredentialStore {
    private OAuthCredential credential;

    @Override
    public synchronized Optional<OAuthCredential> load(CredentialBinding binding) {
        return credential != null && credential.binding().equals(binding) ? Optional.of(credential) : Optional.empty();
    }

    @Override
    public synchronized void save(CredentialBinding binding, OAuthCredential credential) {
        if (!binding.equals(credential.binding())) {
            throw new IllegalArgumentException("Credential binding does not match");
        }
        this.credential = credential;
    }
}
