package com.mcpscanner.access.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mcpscanner.mcp.McpToolDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mcpscanner.access.mining.ScriptedServer.stringArgument;
import static com.mcpscanner.access.mining.ScriptedServer.unannotatedTool;
import static org.assertj.core.api.Assertions.assertThat;

class RunWriteApprovalTest {

    private static final McpToolDefinition ARCHIVE = unannotatedTool("archive_document", stringArgument("doc_id"));

    private final List<List<MiningCall.ToolCall>> shown = new ArrayList<>();
    private boolean answer = true;
    private final RunWriteApproval approval = new RunWriteApproval((user, calls) -> {
        shown.add(calls);
        return answer;
    });

    @Test
    void asksAgainOnlyForCallsCarryingIdsNotShownYet() {
        assertThat(approval.approve("alice", List.of(call(Map.of())))).isTrue();
        assertThat(approval.approve("alice", List.of(call(Map.of("doc_id", "d-1")), call(Map.of())))).isTrue();
        assertThat(approval.approve("bob", List.of(call(Map.of("doc_id", "d-1"))))).isTrue();
        assertThat(approval.approve("bob", List.of(call(Map.of("doc_id", "d-1")), call(Map.of("doc_id", "d-2")))))
                .isTrue();

        assertThat(shown).extracting(calls -> calls.stream().map(call -> call.arguments().toString()).toList())
                .containsExactly(List.of("{}"), List.of("{doc_id=\"d-1\"}"), List.of("{doc_id=\"d-2\"}"));
    }

    @Test
    void aRefusalStandsForTheRestOfTheRun() {
        answer = false;

        assertThat(approval.approve("alice", List.of(call(Map.of())))).isFalse();
        assertThat(approval.approve("alice", List.of(call(Map.of("doc_id", "d-1"))))).isFalse();

        assertThat(shown).hasSize(1);
    }

    @Test
    void refusingNewIdsRefusesThatBatchAndEveryLaterOne() {
        approval.approve("alice", List.of(call(Map.of())));
        answer = false;

        assertThat(approval.approve("alice", List.of(call(Map.of("doc_id", "d-1"))))).isFalse();
        assertThat(approval.approve("alice", List.of(call(Map.of())))).isFalse();
    }

    private static MiningCall.ToolCall call(Map<String, String> arguments) {
        Map<String, JsonNode> values = new java.util.HashMap<>();
        arguments.forEach((name, value) -> values.put(name, TextNode.valueOf(value)));
        return new MiningCall.ToolCall(ARCHIVE, values);
    }
}
