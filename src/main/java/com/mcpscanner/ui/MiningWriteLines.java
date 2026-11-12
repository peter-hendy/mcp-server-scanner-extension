package com.mcpscanner.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.access.mining.MiningCall;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

final class MiningWriteLines {

    private static final String LEAD = "Collecting IDs will call these write tools as %s, with every argument shown below, "
            + "real IDs included. You are asked again before any later call carries an ID not shown yet; refusing "
            + "stops write calls for the rest of this run. This can modify, create, or delete data on the target — "
            + "irreversible.";

    private MiningWriteLines() {
    }

    static DestructiveScanConfirmation.Lines of(String user, List<MiningCall.ToolCall> calls) {
        long tools = calls.stream().map(call -> call.tool().name()).distinct().count();
        String count = calls.size() + (calls.size() == 1 ? " write call" : " write calls") + " to " + tools
                + (tools == 1 ? " tool" : " tools") + " will be sent while collecting IDs:";
        return new DestructiveScanConfirmation.Lines(LEAD.formatted(user), count, calls.stream().map(MiningWriteLines::line).toList(),
                DestructiveScanConfirmation.Purpose.MINING);
    }

    static String line(MiningCall.ToolCall call) {
        JsonNode sent = call.params().path("arguments");
        return "• " + call.tool().name() + " " + (sent.isEmpty() ? "(no arguments)" : arguments(sent));
    }

    private static String arguments(JsonNode sent) {
        Map<String, JsonNode> arguments = new TreeMap<>();
        sent.fields().forEachRemaining(argument -> arguments.put(argument.getKey(), argument.getValue()));
        return arguments.entrySet().stream()
                .map(argument -> argument.getKey() + "=" + text(argument.getValue()))
                .collect(Collectors.joining(", "));
    }

    private static String text(JsonNode value) {
        return value.isValueNode() ? value.asText() : value.toString();
    }
}
