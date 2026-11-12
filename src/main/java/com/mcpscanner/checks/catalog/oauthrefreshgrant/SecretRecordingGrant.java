package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import com.mcpscanner.auth.grant.IssuedTokens;
import com.mcpscanner.auth.grant.LiveRefreshGrant;
import com.mcpscanner.auth.grant.RefreshGrant;

import java.util.HashSet;
import java.util.Set;

final class SecretRecordingGrant implements LiveRefreshGrant {

    private final LiveRefreshGrant delegate;
    private final Set<String> secrets = new HashSet<>();

    SecretRecordingGrant(LiveRefreshGrant delegate) {
        this.delegate = delegate;
    }

    @Override
    public RefreshGrant grant() {
        RefreshGrant grant = delegate.grant();
        record(grant);
        return grant;
    }

    void recordCurrentGrant() {
        record(delegate.grant());
    }

    private void record(RefreshGrant grant) {
        secrets.add(grant.refreshToken());
        grant.client().secret().ifPresent(secret -> {
            secrets.add(secret);
            secrets.add(BasicCredentials.encoded(grant.client().clientId(), secret));
        });
    }

    @Override
    public void adopt(IssuedTokens tokens) {
        secrets.add(tokens.accessToken());
        tokens.refreshToken().ifPresent(secrets::add);
        delegate.adopt(tokens);
    }

    Set<String> secrets() {
        return Set.copyOf(secrets);
    }
}
