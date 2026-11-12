package com.mcpscanner.checks.registry;

import burp.api.montoya.http.Http;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.List;

@FunctionalInterface
public interface CheckInstrumentation {

    CheckInstrumentation NONE = (checkId, http) -> http;

    Http probeHttp(String checkId, Http http);

    default void reported(String checkId, List<AuditIssue> issues) {
    }
}
