package com.mcpscanner.core.connection;

import java.util.Map;
import com.mcpscanner.core.protocol.McpFlow;

public interface SseStreamFactory {
    Stream open(Map<String, String> headers);
    interface Stream extends McpFlow.SsePort, AutoCloseable {
        @Override void close();
    }
}
