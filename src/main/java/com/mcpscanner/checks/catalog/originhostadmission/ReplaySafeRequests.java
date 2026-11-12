package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.SkipReason;
import com.mcpscanner.checks.shared.jsonrpc.JsonRpcEnvelope;
import com.mcpscanner.mcp.McpMethods;

import java.util.Optional;
import java.util.Set;

final class ReplaySafeRequests {

    private static final Set<String> READ_ONLY_METHODS = Set.of(
            McpMethods.TOOLS_LIST, McpMethods.RESOURCES_LIST, McpMethods.RESOURCE_TEMPLATES_LIST,
            McpMethods.PROMPTS_LIST, McpMethods.RESOURCES_READ, McpMethods.PROMPTS_GET);

    private static final SkipReason NOT_REPLAY_SAFE =
            SkipReason.notApplicable("replays only read-only listings, reads and prompt gets");

    Optional<SkipReason> whyRefused(HttpRequest request) {
        return accepts(request) ? Optional.empty() : Optional.of(NOT_REPLAY_SAFE);
    }

    boolean accepts(HttpRequest request) {
        return "POST".equals(request.method()) && isReadOnlyCall(JsonRpcEnvelope.of(request));
    }

    private static boolean isReadOnlyCall(JsonRpcEnvelope message) {
        return message.isJsonRpc2() && message.hasScalarId()
                && message.method().filter(READ_ONLY_METHODS::contains).isPresent();
    }
}
