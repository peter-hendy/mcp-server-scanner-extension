package com.mcpscanner.checks.api;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.insertionpoint.AuditInsertionPoint;

import java.util.List;

@FunctionalInterface
public interface InsertionPointSource {

    List<AuditInsertionPoint> insertionPointsFor(HttpRequestResponse baseRequestResponse);
}
