package com.mcpscanner.ui;

import com.mcpscanner.auth.AccessToken;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.SignedInTokens;
import com.mcpscanner.auth.oauth.SignInEvidence;
import com.mcpscanner.auth.oauth.discovery.DiscoveredMetadata;
import com.mcpscanner.ui.serverinfo.AuthDiscoveryReport;

import java.net.URI;
import java.util.Optional;
import java.util.function.Function;

final class AuthDiscoveryReports {

    private final Function<URI, Optional<DiscoveredMetadata>> lastDiscovery;

    AuthDiscoveryReports(Function<URI, Optional<DiscoveredMetadata>> lastDiscovery) {
        this.lastDiscovery = lastDiscovery;
    }

    AuthDiscoveryReport after(String endpoint, AuthStrategy connectedAs) {
        Optional<SignInEvidence> signIn = connectedAs.signInEvidence();
        return new AuthDiscoveryReport(
                ConnectedEndpoint.of(endpoint).flatMap(lastDiscovery),
                signIn,
                signIn.flatMap(evidence -> connectedAs.signedInTokens())
                        .map(SignedInTokens::accessToken)
                        .flatMap(AccessToken::expiresAt));
    }
}
