package com.mcpscanner.ui.serverinfo;

import com.mcpscanner.auth.oauth.SignInEvidence;
import com.mcpscanner.auth.oauth.discovery.DiscoveredMetadata;

import java.time.Instant;
import java.util.Optional;

public record AuthDiscoveryReport(Optional<DiscoveredMetadata> discovery,
                                  Optional<SignInEvidence> signIn,
                                  Optional<Instant> tokenExpiry) {

    private static final AuthDiscoveryReport NONE =
            new AuthDiscoveryReport(Optional.empty(), Optional.empty(), Optional.empty());

    public static AuthDiscoveryReport none() {
        return NONE;
    }

    public boolean isEmpty() {
        return discovery.isEmpty() && signIn.isEmpty();
    }

    Optional<String> authorizationServerMetadataJson() {
        return signIn.map(SignInEvidence::authorizationServerMetadataJson)
                .or(() -> discovery.map(found -> found.asMetadata().toJSONObject().toJSONString()));
    }

    Optional<String> registrationResponseJson() {
        return signIn.flatMap(SignInEvidence::registrationResponseJson);
    }
}
