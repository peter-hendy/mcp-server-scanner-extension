package com.mcpscanner.auth.oauth.discovery;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.http.BareRequests;

import java.net.URI;
import java.util.Optional;

final class AuthChallengeRequest {

    private final Http http;

    AuthChallengeRequest(Http http) {
        this.http = http;
    }

    Optional<HttpResponse> send(URI mcpResource) {
        HttpRequest request = BareRequests.fromUrl(mcpResource.toString())
                .withMethod("POST")
                .withAddedHeader("Content-Type", "application/json")
                .withBody("{}");
        return Optional.ofNullable(http.sendRequest(request, DiscoveryRequestOptions.noRedirect()).response());
    }
}
