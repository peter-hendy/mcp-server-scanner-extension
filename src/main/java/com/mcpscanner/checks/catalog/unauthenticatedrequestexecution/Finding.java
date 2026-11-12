package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.HttpRequestResponse;

import java.util.List;

record Finding(FindingKind kind, AuditSubject subject, String variantName, String variantValue,
               List<HttpRequestResponse> evidence) {

    Finding {
        evidence = List.copyOf(evidence);
    }
}
