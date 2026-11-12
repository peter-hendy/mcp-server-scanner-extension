package com.mcpscanner.auth.oauth.discovery;

import burp.api.montoya.http.Http;

import java.net.URI;
import java.util.Optional;

final class PostChallengeSender implements ChallengeSender {

    private final AuthChallengeRequest request;

    PostChallengeSender(Http http) {
        this.request = new AuthChallengeRequest(http);
    }

    @Override
    public Optional<ChallengeResponse> send(URI mcpResource) {
        return request.send(mcpResource).map(response -> new ChallengeResponse(
                response.statusCode(), Optional.ofNullable(response.headerValue("WWW-Authenticate"))));
    }
}
