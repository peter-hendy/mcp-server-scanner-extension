package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static java.util.Map.entry;

import static org.assertj.core.api.Assertions.assertThat;

class ParamHeadersTest {

    private static final ParamHeaderBinding TENANT = new ParamHeaderBinding("Mcp-Param-Tenant", List.of("tenant"));
    private static final ParamHeaderBinding PRIORITY = new ParamHeaderBinding("Mcp-Param-Priority", List.of("priority"));
    private static final ParamHeaderBinding DRY_RUN =
            new ParamHeaderBinding("Mcp-Param-Dry-Run", List.of("options", "dryRun"));
    private static final ParamHeaders HEADERS = new ParamHeaders(List.of(TENANT, PRIORITY, DRY_RUN));

    @Test
    void rendersStringsIntegersAndBooleans() {
        assertThat(HEADERS.valuesFor(json("""
                {"tenant":"acme","priority":42,"options":{"dryRun":true}}""")))
                .containsExactly(
                        entry("Mcp-Param-Tenant", "acme"),
                        entry("Mcp-Param-Priority", "42"),
                        entry("Mcp-Param-Dry-Run", "true"));
    }

    @Test
    void rendersBigDecimalsInPlainNotation() {
        ObjectNode arguments = McpObjectMapper.INSTANCE.createObjectNode();
        arguments.set("priority", DecimalNode.valueOf(new BigDecimal("1E+3")));

        assertThat(HEADERS.valuesFor(arguments)).containsEntry("Mcp-Param-Priority", "1000");
    }

    @Test
    void rendersBigIntegersInFull() {
        assertThat(HEADERS.valuesFor(json("{\"priority\":12345678901234567890123}")))
                .containsEntry("Mcp-Param-Priority", "12345678901234567890123");
    }

    @Test
    void rendersParsedIntegralFloatsAsIntegers() {
        assertThat(HEADERS.valuesFor(json("{\"priority\":42.0}"))).containsEntry("Mcp-Param-Priority", "42");
        assertThat(HEADERS.valuesFor(json("{\"priority\":1e16}")))
                .containsEntry("Mcp-Param-Priority", "10000000000000000");
        assertThat(HEADERS.valuesFor(json("{\"priority\":-7.00}"))).containsEntry("Mcp-Param-Priority", "-7");
    }

    @Test
    void rendersParsedFractionsInPlainNotation() {
        assertThat(HEADERS.valuesFor(json("{\"priority\":1.5}"))).containsEntry("Mcp-Param-Priority", "1.5");
        assertThat(HEADERS.valuesFor(json("{\"priority\":1e-7}")))
                .containsEntry("Mcp-Param-Priority", "0.0000001");
    }

    @Test
    void encodesValuesThatCannotTravelPlain() {
        assertThat(HEADERS.valuesFor(json("{\"tenant\":\" acme\"}")))
                .containsEntry("Mcp-Param-Tenant", "=?base64?IGFjbWU=?=");
    }

    @Test
    void omitsAbsentNullObjectAndArrayValues() {
        assertThat(HEADERS.valuesFor(json("""
                {"tenant":null,"priority":[1],"options":{"dryRun":{"x":1}}}"""))).isEmpty();
        assertThat(HEADERS.valuesFor(json("{\"options\":\"flat\"}"))).isEmpty();
        assertThat(HEADERS.valuesFor(json("{}"))).isEmpty();
    }

    @Test
    void rendersNothingForNonObjectArguments() {
        assertThat(HEADERS.valuesFor(json("null"))).isEmpty();
        assertThat(HEADERS.valuesFor(json("[\"acme\"]"))).isEmpty();
    }

    @Test
    void noneRendersNothing() {
        assertThat(ParamHeaders.NONE.valuesFor(json("{\"tenant\":\"acme\"}"))).isEmpty();
    }

    private static JsonNode json(String text) {
        try {
            return McpObjectMapper.INSTANCE.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
