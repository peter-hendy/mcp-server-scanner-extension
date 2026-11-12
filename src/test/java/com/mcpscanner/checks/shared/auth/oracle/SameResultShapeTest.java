package com.mcpscanner.checks.shared.auth.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SameResultShapeTest {

    private final SameResultShape shape = new SameResultShape();

    @Test
    void sameKeysAndTypesWithDifferentValuesAreEquivalent() {
        assertThat(shape.equivalent(json("{\"a\":1,\"b\":\"x\"}"), json("{\"b\":\"y\",\"a\":2}"))).isTrue();
    }

    @Test
    void arraysOfDifferentLengthWithTheSameElementShapeAreEquivalent() {
        assertThat(shape.equivalent(json("{\"c\":[{\"t\":\"a\"}]}"), json("{\"c\":[{\"t\":\"a\"},{\"t\":\"b\"}]}")))
                .isTrue();
    }

    @Test
    void missingKeysDifferentTypesOrDifferentElementShapesAreNotEquivalent() {
        assertThat(shape.equivalent(json("{\"a\":1}"), json("{\"a\":1,\"b\":2}"))).isFalse();
        assertThat(shape.equivalent(json("{\"a\":1}"), json("{\"a\":\"1\"}"))).isFalse();
        assertThat(shape.equivalent(json("{\"c\":[{\"t\":\"a\"}]}"), json("{\"c\":[{\"u\":\"a\"}]}"))).isFalse();
        assertThat(shape.equivalent(json("{\"c\":[{\"t\":\"a\"}]}"), json("{\"c\":[]}"))).isFalse();
    }

    private static JsonNode json(String text) {
        try {
            return McpObjectMapper.INSTANCE.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
