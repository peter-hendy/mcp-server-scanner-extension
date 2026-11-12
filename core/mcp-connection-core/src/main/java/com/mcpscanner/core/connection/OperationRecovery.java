package com.mcpscanner.core.connection;

import com.mcpscanner.core.protocol.McpFlow;

public interface OperationRecovery {
    RecoveryResult recover(McpFlow.HttpResponse response);
}
