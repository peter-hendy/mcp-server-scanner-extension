package com.mcpscanner.mcp;

import java.util.Map;

public interface SseProbeStreams {

    SseProbeStream open(Map<String, String> headers);
}
