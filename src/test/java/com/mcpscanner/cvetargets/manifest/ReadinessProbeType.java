package com.mcpscanner.cvetargets.manifest;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ReadinessProbeType {
    @JsonProperty("mcp-initialize") MCP_INITIALIZE,
    @JsonProperty("sse-endpoint") SSE_ENDPOINT
}
