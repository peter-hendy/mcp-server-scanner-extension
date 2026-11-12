package com.mcpscanner.access.mining;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RunWriteApproval implements WriteCallApproval {

    private final WriteCallApproval approval;
    private final Set<String> shownIds = new HashSet<>();
    private boolean asked;
    private boolean approved = true;

    RunWriteApproval(WriteCallApproval approval) {
        this.approval = approval;
    }

    @Override
    public synchronized boolean approve(String user, List<MiningCall.ToolCall> calls) {
        if (!approved) {
            return false;
        }
        List<MiningCall.ToolCall> unseen = asked ? calls.stream().filter(this::carriesUnseenIds).toList() : calls;
        if (!unseen.isEmpty()) {
            asked = true;
            approved = approval.approve(user, unseen);
            calls.forEach(call -> shownIds.addAll(ids(call)));
        }
        return approved;
    }

    private boolean carriesUnseenIds(MiningCall.ToolCall call) {
        return !shownIds.containsAll(ids(call));
    }

    private static Set<String> ids(MiningCall.ToolCall call) {
        Set<String> ids = new HashSet<>();
        for (Map.Entry<String, JsonNode> argument : call.arguments().entrySet()) {
            ids.add(argument.getKey() + "=" + argument.getValue());
        }
        return ids;
    }
}
