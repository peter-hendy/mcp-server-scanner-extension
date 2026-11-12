package com.mcpscanner.access;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.List;

public interface AccessComparisonRun extends AutoCloseable {

    List<AuditIssue> compare(HttpRequestResponse ownerExchange);

    @Override
    void close();
}
