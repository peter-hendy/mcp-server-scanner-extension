package com.mcpscanner.auth.oauth.discovery;

final class DiscoveryTrail {

    private String challenge;
    private String challengeFailure;
    private String protectedResourceFailure;

    void challengeReceived(String rawChallenge) {
        challenge = rawChallenge;
    }

    void challengeFailed(String reason) {
        challengeFailure = reason;
    }

    void protectedResourceFetchFailed(String reason) {
        protectedResourceFailure = reason;
    }

    DiscoveredMetadata applyTo(DiscoveredMetadata metadata) {
        if (isEmpty()) {
            return metadata;
        }
        DiscoveredMetadata withChallenge = metadata.wwwAuthenticateChallenge() == null && challenge != null
                ? metadata.withWwwAuthenticateChallenge(challenge) : metadata;
        return withChallenge.withFetchFailures(new MetadataFetchFailures(
                withChallenge.protectedResource() == null ? protectedResourceFailure : null,
                withChallenge.wwwAuthenticateChallenge() == null ? challengeFailure : null));
    }

    private boolean isEmpty() {
        return challenge == null && challengeFailure == null && protectedResourceFailure == null;
    }
}
