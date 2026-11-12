package com.mcpscanner.checks.catalog.commandinjection;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyTextTest {

    @Test
    void searchesDecodedTextAtAnyDepthButNotKeysOrNumbers() throws Exception {
        JsonNode node = McpObjectMapper.INSTANCE.readTree("{\"mcp42\":1,\"a\":[2,{\"b\":\"x\\u006dcp42y\"}]}");

        assertThat(ReplyText.anyContains(node, "mcp42")).isTrue();
        assertThat(ReplyText.anyContains(McpObjectMapper.INSTANCE.readTree("{\"mcp42\":42}"), "mcp42")).isFalse();
    }

    @Test
    void checksTheStartOfEachContentText() throws Exception {
        JsonNode result = McpObjectMapper.INSTANCE.readTree("{\"content\":[{\"type\":\"image\"},{\"text\":\"Error x\"}]}");

        assertThat(ReplyText.contentTextStartsWith(result, "Error")).isTrue();
        assertThat(ReplyText.contentTextStartsWith(result, "x")).isFalse();
    }
}
