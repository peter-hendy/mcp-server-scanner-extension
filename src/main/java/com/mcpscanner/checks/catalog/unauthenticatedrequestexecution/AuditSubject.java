package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.HostKey;
import com.mcpscanner.checks.shared.probe.McpMethodClass;

record AuditSubject(HttpRequestResponse baseline, SeedMessage message, boolean credentialsConfigured) {

    HttpRequest seed() {
        return baseline.request();
    }

    HostKey host() {
        return HostKey.of(baseline.httpService());
    }

    McpMethodClass methodClass() {
        return message.methodClass();
    }
}
