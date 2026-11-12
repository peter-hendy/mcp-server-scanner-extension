package com.mcpscanner.access.mining;

import com.mcpscanner.mcp.ToolSafety;
import java.util.List;

final class WriteCallScreen {

    private final WriteCallApproval approval;

    WriteCallScreen(WriteCallApproval approval) {
        this.approval = approval;
    }

    List<MiningCall> approved(String user, List<MiningCall> calls) {
        List<MiningCall.ToolCall> writes = calls.stream().filter(WriteCallScreen::writes)
                .map(MiningCall.ToolCall.class::cast).toList();
        if (writes.isEmpty() || approval.approve(user, writes)) {
            return calls;
        }
        return calls.stream().filter(call -> !writes(call)).toList();
    }

    private static boolean writes(MiningCall call) {
        return call instanceof MiningCall.ToolCall toolCall && ToolSafety.needsOptIn(toolCall.tool());
    }
}
