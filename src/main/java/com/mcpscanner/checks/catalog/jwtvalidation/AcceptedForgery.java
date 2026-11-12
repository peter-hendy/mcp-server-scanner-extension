package com.mcpscanner.checks.catalog.jwtvalidation;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.List;

record AcceptedForgery(String description, HttpRequestResponse candidate,
                       List<HttpRequestResponse> refusedControls) {

    AcceptedForgery {
        refusedControls = List.copyOf(refusedControls);
    }
}
