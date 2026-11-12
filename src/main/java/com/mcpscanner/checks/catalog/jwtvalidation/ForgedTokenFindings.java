package com.mcpscanner.checks.catalog.jwtvalidation;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.List;

record ForgedTokenFindings(HttpRequestResponse baseline, List<HttpRequestResponse> refusalControls,
                           List<AcceptedForgery> acceptedForgeries, List<ClaimProbe> claimProbes) {

    ForgedTokenFindings {
        refusalControls = List.copyOf(refusalControls);
        acceptedForgeries = List.copyOf(acceptedForgeries);
        claimProbes = List.copyOf(claimProbes);
    }
}
