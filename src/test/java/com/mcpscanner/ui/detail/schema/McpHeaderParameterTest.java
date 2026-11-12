package com.mcpscanner.ui.detail.schema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mcpscanner.ui.detail.schema.SchemaFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

class McpHeaderParameterTest {

    private static final String SCHEMA = """
            {"type":"object","properties":{
              "region":{"type":"string","x-mcp-header":"Region"},
              "query":{"type":"string"},
              "filters":{"type":"object","properties":{"tenant":{"type":"string","x-mcp-header":"Tenant"}}}
            }}""";

    @Test
    void marksTopLevelPropertiesWithTheHeaderTheyProduce() {
        List<SchemaRow> rows = SchemaOutline.of(json(SCHEMA)).rows();

        assertThat(rows).extracting(SchemaRow::name, SchemaRow::mcpHeader).containsExactly(
                org.assertj.core.groups.Tuple.tuple("region", "Mcp-Param-Region"),
                org.assertj.core.groups.Tuple.tuple("query", null),
                org.assertj.core.groups.Tuple.tuple("filters", null),
                org.assertj.core.groups.Tuple.tuple("tenant", null));
    }

    @Test
    void topLevelAnnotationIsNotReportedAsAnOtherKeyword() {
        assertThat(SchemaOutline.of(json(SCHEMA)).otherKeywords()).containsExactly("filters.tenant.x-mcp-header: \"Tenant\"");
    }
}
