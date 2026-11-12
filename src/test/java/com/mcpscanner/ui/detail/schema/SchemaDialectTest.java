package com.mcpscanner.ui.detail.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.mcpscanner.ui.detail.schema.SchemaFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

class SchemaDialectTest {

    @ParameterizedTest
    @CsvSource({
            "http://json-schema.org/draft-04/schema#, draft-04",
            "http://json-schema.org/draft-06/schema#, draft-06",
            "http://json-schema.org/draft-07/schema#, draft-07",
            "http://json-schema.org/draft-07/schema, draft-07",
            "https://json-schema.org/draft-07/schema#, draft-07",
            "https://json-schema.org/draft/2019-09/schema, 2019-09",
            "https://json-schema.org/draft/2020-12/schema, 2020-12",
            "http://json-schema.org/draft/2020-12/schema#, 2020-12",
            "https://example.com/my-dialect, https://example.com/my-dialect"
    })
    void explicitSchemaGivesShortNameForKnownMetaSchemas(String uri, String expected) {
        assertThat(SchemaDialect.label(json("{\"$schema\":\"" + uri + "\"}"), "2024-11-05")).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "NULL, unspecified",
            "2024-11-05, unspecified",
            "2025-03-26, unspecified",
            "2025-06-18, unspecified",
            "2025-11-25, 2020-12 (default)",
            "2026-07-28, 2020-12 (default)"
    })
    void absentSchemaDependsOnNegotiatedVersion(String protocolVersion, String expected) {
        assertThat(SchemaDialect.label(json("{\"type\":\"object\"}"), protocolVersion)).isEqualTo(expected);
    }

    @Test
    void explicitSchemaWinsOverVersionDefault() {
        assertThat(SchemaDialect.label(json(SchemaFixtures.PETERS_SCHEMA), "2025-11-25")).isEqualTo("draft-07");
    }

    @Test
    void nonObjectSchemaIsTreatedAsAbsent() {
        assertThat(SchemaDialect.label(json("[1]"), "2025-06-18")).isEqualTo("unspecified");
        assertThat(SchemaDialect.label(null, "2025-11-25")).isEqualTo("2020-12 (default)");
    }
}
