package com.mcpscanner.checks.catalog.filetraversal;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.List;

record Finding(FindingKind kind, List<String> rejectedLiterals, List<String> observations,
               List<HttpRequestResponse> evidence) {

    Finding {
        rejectedLiterals = List.copyOf(rejectedLiterals);
        observations = List.copyOf(observations);
        evidence = List.copyOf(evidence);
    }

    Finding(FindingKind kind, List<String> observations, List<HttpRequestResponse> evidence) {
        this(kind, List.of(), observations, evidence);
    }

    String meaning() {
        return kind.meaning(rejectedLiterals);
    }
}
