package com.mcpscanner.checks.catalog.oauthauthorizationconsent;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

final class FindingDraft {

    private final URI location;
    private final List<String> facts = new ArrayList<>();
    private final List<HttpRequestResponse> evidence = new ArrayList<>();

    FindingDraft(URI location) {
        this.location = location;
    }

    FindingDraft fact(String fact) {
        facts.add(fact);
        return this;
    }

    FindingDraft evidence(List<HttpRequestResponse> exchanges) {
        evidence.addAll(exchanges);
        return this;
    }

    ConsentFinding as(FindingKind kind) {
        return new ConsentFinding(kind, location, facts, evidence);
    }
}
