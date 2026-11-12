package com.mcpscanner.automation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.scan.request.GeneratedScan;
import com.mcpscanner.scan.request.McpMessage;
import com.mcpscanner.scan.request.RequestFamily;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeedMessagesTest {

    private final SeedMessages seeds = new SeedMessages();
    private final McpMessage generated = McpMessage.parameterlessRequest(RequestFamily.PING, "ping");

    @Test
    void appendsEachSeedAfterTheGeneratedRequestsAsANamedCall() {
        ObjectNode params = McpObjectMapper.INSTANCE.createObjectNode().put("name", "read_note");

        GeneratedScan scan = seeds.appendTo(new GeneratedScan(List.of(generated), List.of()),
                List.of(new SeedCall("tools/call", params)));

        assertThat(scan.messages()).hasSize(2).first().isEqualTo(generated);
        McpMessage seed = (McpMessage) scan.messages().get(1);
        assertThat(seed.family()).isEqualTo(RequestFamily.TOOLS_CALL);
        assertThat(seed.subject()).isEqualTo("tools/call read_note");
        assertThat(seed.params()).contains(params);
    }

    @Test
    void aSeedWithoutParamsGetsAnEmptyParamsObject() {
        GeneratedScan scan = seeds.appendTo(new GeneratedScan(List.of(), List.of()),
                List.of(new SeedCall("tools/list", null)));

        McpMessage seed = (McpMessage) scan.messages().get(0);
        assertThat(seed.family()).isEqualTo(RequestFamily.TOOLS_LIST);
        assertThat(seed.params()).hasValueSatisfying(params -> assertThat(params.isEmpty()).isTrue());
    }

    @Test
    void rejectsASeedMethodItCannotFrame() {
        assertThatThrownBy(() -> seeds.appendTo(new GeneratedScan(List.of(), List.of()),
                List.of(new SeedCall("sampling/createMessage", null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sampling/createMessage");
    }
}
