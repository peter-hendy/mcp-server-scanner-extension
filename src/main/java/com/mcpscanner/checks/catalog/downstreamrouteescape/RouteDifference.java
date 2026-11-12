package com.mcpscanner.checks.catalog.downstreamrouteescape;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.List;

record RouteDifference(RouteProbe probe, List<HttpRequestResponse> evidence) {

    RouteDifference {
        evidence = List.copyOf(evidence);
    }
}
