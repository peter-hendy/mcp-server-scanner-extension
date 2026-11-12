package com.mcpscanner.access.mining;

import java.util.List;

@FunctionalInterface
public interface WriteCallApproval {

    WriteCallApproval REFUSE = (user, calls) -> false;

    boolean approve(String user, List<MiningCall.ToolCall> calls);
}
