package com.mcpscanner.testutil;

import com.mcpscanner.mcp.McpOperationFraming;
import com.mcpscanner.core.protocol.McpRequests;

import java.util.Map;

public final class PlainFraming {

    @FunctionalInterface
    public interface Operations {
        McpRequests.Request operation(String method, String paramsJson, long id);
    }

    private PlainFraming() {
    }

    public static McpOperationFraming.Framer framer() {
        return withOperations((method, paramsJson, id) ->
                new McpRequests.Request(McpRequests.envelope(method, paramsJson, id), Map.of()));
    }

    public static McpOperationFraming.Framer withOperations(Operations operations) {
        return new McpOperationFraming.Framer() {
            @Override
            public McpRequests.Request operation(String method, String paramsJson, long id) {
                return operations.operation(method, paramsJson, id);
            }

            @Override
            public McpRequests.Request notification(String method, String paramsJson) {
                return new McpRequests.Request(
                        "{\"jsonrpc\":\"2.0\",\"method\":\"" + method + "\",\"params\":" + paramsJson + "}", Map.of());
            }
        };
    }
}
