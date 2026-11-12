package com.mcpscanner.checks.catalog.downstreamrouteescape;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.mcp.McpRequestDetector;
import com.mcpscanner.mcp.McpRequestKind;

import java.util.Set;

final class CallRequests {

    private static final Set<McpRequestKind> CALLS = Set.of(
            McpRequestKind.TOOLS_CALL, McpRequestKind.PROMPTS_GET, McpRequestKind.RESOURCES_READ);

    private final ReplySignature signatures;

    CallRequests(ReplySignature signatures) {
        this.signatures = signatures;
    }

    boolean isCall(HttpRequestResponse base) {
        return CALLS.contains(McpRequestDetector.classify(base));
    }

    boolean succeeded(HttpRequestResponse base, String baseValue) {
        return base.response() != null && signatures.ofSuccess(base, baseValue).isPresent();
    }
}
