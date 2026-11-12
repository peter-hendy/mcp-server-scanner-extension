package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.shared.oauth.OAuthHttp;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

final class Authorizer {

    private static final String BROWSER_ACCEPT = "text/html,application/xhtml+xml";

    private final OAuthHttp http;
    private final AuthorizationRequestFactory requests;
    private final Map<String, String> sessionHeaders;

    Authorizer(OAuthHttp http, AuthorizationRequestFactory requests, Map<String, String> sessionHeaders) {
        this.http = http;
        this.requests = requests;
        this.sessionHeaders = Map.copyOf(sessionHeaders);
    }

    AuthorizationAttempt authorize(String clientId, URI callback, String state, AuthorizationVariant variant) {
        Optional<HttpRequestResponse> exchange = variant.submitsForm()
                ? http.postForm(requests.endpoint(), requests.form(clientId, callback, state, variant), BROWSER_ACCEPT,
                        sessionHeaders)
                : http.get(requests.build(clientId, callback, state, variant), BROWSER_ACCEPT, sessionHeaders);
        AuthorizationOutcome outcome = exchange
                .map(response -> new AuthorizationOutcome(response.response().statusCode(),
                        response.response().headerValue("Location"), true))
                .orElseGet(AuthorizationOutcome::incomplete);
        return new AuthorizationAttempt(outcome, exchange);
    }
}
