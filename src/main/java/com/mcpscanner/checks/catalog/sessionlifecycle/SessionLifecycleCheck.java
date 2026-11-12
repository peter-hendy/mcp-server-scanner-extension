package com.mcpscanner.checks.catalog.sessionlifecycle;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.HostCheck;

import java.util.List;

public final class SessionLifecycleCheck implements HostCheck {

    private final SessionLifecycleAudit audit;

    SessionLifecycleCheck(SessionLifecycleAudit audit) {
        this.audit = audit;
    }

    @Override
    public CheckDescriptor descriptor() {
        return SessionLifecycleDescriptor.DESCRIPTOR;
    }

    @Override
    public boolean appliesTo(HttpRequestResponse hostBaseRequestResponse) {
        return audit.appliesTo(hostBaseRequestResponse.request())
                && hostBaseRequestResponse.response() != null
                && hostBaseRequestResponse.response().statusCode() / 100 == 2;
    }

    @Override
    public String appliesOnlyTo() {
        return "answered (2xx) MCP requests in a server-issued session";
    }

    @Override
    public List<AuditIssue> audit(HttpRequestResponse hostBaseRequestResponse, Http http) {
        return audit.run(hostBaseRequestResponse, http);
    }
}
