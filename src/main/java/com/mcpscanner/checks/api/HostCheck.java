package com.mcpscanner.checks.api;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;

import java.util.List;

public non-sealed interface HostCheck extends McpCheck {

    boolean appliesTo(HttpRequestResponse hostBaseRequestResponse);

    String appliesOnlyTo();

    List<AuditIssue> audit(HttpRequestResponse hostBaseRequestResponse, Http http);

    default List<HostTrial> trials() {
        return List.of(new WholeCheckTrial(this));
    }

    @Override
    default <R> R accept(CheckVisitor<R> visitor) {
        return visitor.visitHostCheck(this);
    }
}
