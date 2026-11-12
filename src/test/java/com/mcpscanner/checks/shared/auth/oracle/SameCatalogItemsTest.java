package com.mcpscanner.checks.shared.auth.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SameCatalogItemsTest {

    private final SameCatalogItems equivalence = new SameCatalogItems();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"tools\":[{\"name\":\"a\"},{\"name\":\"b\"}]} | {\"tools\":[{\"name\":\"b\",\"description\":\"x\"},{\"name\":\"a\"}]}",
            "{\"prompts\":[{\"name\":\"p\"}]} | {\"prompts\":[{\"name\":\"p\"}]}",
            "{\"resources\":[{\"uri\":\"file:///secret\",\"name\":\"s\"}]} | {\"resources\":[{\"uri\":\"file:///secret\",\"name\":\"other\"}]}",
            "{\"resourceTemplates\":[{\"uriTemplate\":\"file:///{p}\"}],\"nextCursor\":\"1\"} | {\"resourceTemplates\":[{\"uriTemplate\":\"file:///{p}\"}]}"
    })
    void listsWithTheSameItemIdentifiersAreEquivalent(String baseline, String candidate) {
        assertThat(equivalence.equivalent(json(baseline), json(candidate))).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"tools\":[{\"name\":\"a\"}]} | {\"tools\":[{\"name\":\"b\"}]}",
            "{\"tools\":[{\"name\":\"a\"}]} | {\"tools\":[{\"name\":\"a\"},{\"name\":\"b\"}]}",
            "{\"resources\":[{\"uri\":\"file:///secret/payroll.csv\"}]} | {\"resources\":[{\"uri\":\"https://public.example/readme\"}]}",
            "{\"tools\":[]} | {\"tools\":[]}",
            "{\"tools\":[{\"name\":\"a\"}]} | {\"prompts\":[{\"name\":\"a\"}]}",
            "{} | {}"
    })
    void differentEmptyOrUnrecognisedListsAreNotEquivalent(String baseline, String candidate) {
        assertThat(equivalence.equivalent(json(baseline), json(candidate))).isFalse();
    }

    @Test
    void itemsWithoutAnIdentifierNeverMakeAListEquivalent() {
        assertThat(equivalence.equivalent(json("{\"tools\":[{\"title\":\"a\"}]}"), json("{\"tools\":[{\"title\":\"a\"}]}")))
                .isFalse();
    }

    private static JsonNode json(String text) {
        try {
            return McpObjectMapper.INSTANCE.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
