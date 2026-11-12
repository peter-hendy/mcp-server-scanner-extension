package com.mcpscanner.scan.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;

final class McpParams {

    static final String PROGRESS_TOKEN = "mcp-scanner-progress";

    private McpParams() {}

    static ObjectNode empty() {
        return McpObjectMapper.INSTANCE.createObjectNode();
    }

    static ObjectNode metaOnly() {
        return withMeta(empty());
    }

    static ObjectNode withMeta(ObjectNode params) {
        params.putObject("_meta").put("progressToken", PROGRESS_TOKEN);
        return params;
    }
}
