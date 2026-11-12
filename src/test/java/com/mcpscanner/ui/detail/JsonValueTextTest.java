package com.mcpscanner.ui.detail;

import org.junit.jupiter.api.Test;

import static com.mcpscanner.testutil.Json.parse;
import static org.assertj.core.api.Assertions.assertThat;

class JsonValueTextTest {

    @Test
    void stringsAreQuotedWithTheirEscapesKept() {
        assertThat(JsonValueText.of(parse("\"say \\\"hi\\\"\\n\""))).isEqualTo("\"say \\\"hi\\\"\\n\"");
    }

    @Test
    void arraysAreCompactWithASpaceAfterEachComma() {
        assertThat(JsonValueText.of(parse("[\"config\",\"prod\"]"))).isEqualTo("[\"config\", \"prod\"]");
    }

    @Test
    void objectsAreCompactAndKeepKeyOrder() {
        assertThat(JsonValueText.of(parse("{\"z\":1,\"a\":{\"x\":[true,null,1.5]}}")))
                .isEqualTo("{\"z\": 1, \"a\": {\"x\": [true, null, 1.5]}}");
    }

    @Test
    void aToolEntryKeepsItsNonAlphabeticalOrder() {
        assertThat(JsonValueText.of(parse("{\"name\":\"t\",\"inputSchema\":{},\"annotations\":{}}")))
                .isEqualTo("{\"name\": \"t\", \"inputSchema\": {}, \"annotations\": {}}");
    }

    @Test
    void emptyContainersStayBalanced() {
        assertThat(JsonValueText.of(parse("[]"))).isEqualTo("[]");
        assertThat(JsonValueText.of(parse("{}"))).isEqualTo("{}");
    }

    @Test
    void scalarsUseTheirJsonForm() {
        assertThat(JsonValueText.of(parse("false"))).isEqualTo("false");
        assertThat(JsonValueText.of(parse("null"))).isEqualTo("null");
        assertThat(JsonValueText.of(parse("-3"))).isEqualTo("-3");
    }

    @Test
    void anAbsentNodeIsEmpty() {
        assertThat(JsonValueText.of(null)).isEmpty();
        assertThat(JsonValueText.of(parse("{}").path("missing"))).isEmpty();
    }
}
