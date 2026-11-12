package com.mcpscanner.checks.api.crossidentity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mcpscanner.mcp.McpObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EmptyContentTest {

    private static JsonNode textResult(String text) {
        return McpObjectMapper.INSTANCE.createObjectNode().set("content", McpObjectMapper.INSTANCE.createArrayNode()
                .add(McpObjectMapper.INSTANCE.createObjectNode().put("type", "text").set("text", TextNode.valueOf(text))));
    }

    private static JsonNode structuredResult(String json) throws Exception {
        return McpObjectMapper.INSTANCE.createObjectNode()
                .set("structuredContent", McpObjectMapper.INSTANCE.readTree(json));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"data\":[],\"total\":0,\"offset\":0}", "0", "[]", "{}", "\"\"", "null", "false",
            "{\"items\":[],\"next\":null,\"more\":false,\"cursor\":\"\"}", "  "})
    void jsonTextWithoutDataIsEmpty(String text) {
        assertThat(EmptyContent.empty(textResult(text))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"data\":[{\"id\":1}],\"total\":1}", "No results", "0 results", "1", "true",
            "{\"data\":[],\"total\":3}", "[0]", "{\"name\":\"x\"}"})
    void textWithAnyDataIsSubstantive(String text) {
        assertThat(EmptyContent.empty(textResult(text))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"data\":[],\"total\":0,\"offset\":0}", "{\"ok\":false,\"count\":0.0}"})
    void structuredContentWithoutDataIsEmpty(String json) throws Exception {
        assertThat(EmptyContent.empty(structuredResult(json))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"data\":[{\"id\":1}],\"total\":1}", "{\"total\":2}"})
    void structuredContentWithDataIsSubstantive(String json) throws Exception {
        assertThat(EmptyContent.empty(structuredResult(json))).isFalse();
    }
}
