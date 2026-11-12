package com.mcpscanner.auth.oauth.discovery;

import java.net.URI;
import java.util.Optional;

interface ChallengeSender {

    Optional<ChallengeResponse> send(URI mcpResource);
}
