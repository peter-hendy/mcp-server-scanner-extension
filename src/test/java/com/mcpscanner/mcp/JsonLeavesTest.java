package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLeavesTest {

    @Test
    void eachLeafKnowsItsKeyItsParentKeyAndItsFullPath() throws Exception {
        JsonNode node = McpObjectMapper.INSTANCE.readTree(
                "{\"id\":1,\"campaigns\":[{\"id\":\"c-1\",\"tags\":[\"a\"]}],\"ok\":true}");

        List<Leaf> leaves = JsonLeaves.under(node, List.of());

        assertThat(leaves).extracting(Leaf::pathText)
                .containsExactly("id", "campaigns[0].id", "campaigns[0].tags[0]");
        assertThat(leaves).extracting(Leaf::name).containsExactly("id", "id", "tags");
        assertThat(leaves).extracting(Leaf::parentKey).containsExactly(null, "campaigns", "campaigns");
    }

    @Test
    void aScalarOrArrayRootHasNoNamedLeaf() throws Exception {
        assertThat(JsonLeaves.under(McpObjectMapper.INSTANCE.readTree("[1,2]"), List.of()))
                .extracting(Leaf::name).containsOnly("");
    }
}
