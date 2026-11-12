package com.mcpscanner.checks.catalog.downstreamrouteescape;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CallSubjectTest {

    @Test
    void toolCallIsNamedByMethodAndTool() {
        assertThat(CallSubject.of("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"get_invoice\",\"arguments\":{}}}")).isEqualTo("tools/call get_invoice");
    }

    @Test
    void resourceReadIsNamedByMethodAndUri() {
        assertThat(CallSubject.of("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\","
                + "\"params\":{\"uri\":\"files:///readme\"}}")).isEqualTo("resources/read files:///readme");
    }

    @Test
    void unreadableBodyIsNamedAsUnknown() {
        assertThat(CallSubject.of("not json")).isEqualTo("unknown MCP request");
    }
}
