package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.net.URI;
import java.util.List;

record RedirectFinding(FindingKind kind, URI location, String summary, List<HttpRequestResponse> evidence) {

    RedirectFinding {
        evidence = List.copyOf(evidence);
    }
}
