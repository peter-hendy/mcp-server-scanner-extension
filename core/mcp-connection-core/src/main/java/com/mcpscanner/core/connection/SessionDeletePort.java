package com.mcpscanner.core.connection;

import com.mcpscanner.core.protocol.McpFlow;
import java.util.Map;

public interface SessionDeletePort {
    McpFlow.HttpResponse delete(Map<String, String> headers);
}
