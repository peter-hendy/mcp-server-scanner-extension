package com.mcpscanner.cvetargets.lifecycle;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.CheckKind;
import com.mcpscanner.checks.api.RequestCheck;

import java.util.List;

public final class McpReplySucceedsCheck implements RequestCheck {

    public static final String ID = "cve-harness-lifecycle-probe";

    static final CheckDescriptor DESCRIPTOR = new CheckDescriptor(
            ID, "CVE harness lifecycle probe", "Reports every base request whose replay returns a JSON-RPC result.",
            CheckKind.REQUEST, AuditIssueSeverity.INFORMATION, AuditIssueConfidence.CERTAIN,
            true, List.of());

    @Override
    public CheckDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public List<AuditIssue> audit(HttpRequestResponse baseRequestResponse, Http http) {
        HttpRequestResponse replay = http.sendRequest(baseRequestResponse.request());
        if (!isJsonRpcResult(replay.response())) {
            return List.of();
        }
        return List.of(AuditIssue.auditIssue(DESCRIPTOR.issueName(), "replayed", "",
                baseRequestResponse.request().url(), DESCRIPTOR.headlineSeverity(), DESCRIPTOR.headlineConfidence(),
                "", "", DESCRIPTOR.headlineSeverity(), replay));
    }

    private static boolean isJsonRpcResult(HttpResponse response) {
        return response != null && response.statusCode() / 100 == 2 && response.bodyToString().contains("\"result\"");
    }
}
