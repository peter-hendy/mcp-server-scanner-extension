package com.mcpscanner.checks.catalog.siblingrouteauthparity;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.List;

record Finding(SiblingRoute route, HttpRequestResponse baseline, List<HttpRequestResponse> evidence) {

    Finding {
        evidence = List.copyOf(evidence);
    }
}
