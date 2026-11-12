package com.mcpscanner.checks.catalog.oauthauthorizationconsent;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.net.URI;
import java.util.List;

record ConsentFinding(FindingKind kind, URI location, List<String> facts, List<HttpRequestResponse> evidence) {

    ConsentFinding {
        facts = List.copyOf(facts);
        evidence = List.copyOf(evidence);
    }
}
