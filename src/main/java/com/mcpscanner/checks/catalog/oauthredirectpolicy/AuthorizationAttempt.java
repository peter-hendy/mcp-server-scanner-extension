package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.Optional;

record AuthorizationAttempt(AuthorizationOutcome outcome, Optional<HttpRequestResponse> exchange) {
}
