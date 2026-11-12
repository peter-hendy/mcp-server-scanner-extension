package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParamHeaderSchemaTest {

    @Test
    void bindsTopLevelAndNestedPropertiesOfPrimitiveType() {
        List<ParamHeaderBinding> bindings = ParamHeaderSchema.bindingsIn(json("""
                {"type":"object","properties":{
                  "tenant":{"type":"string","x-mcp-header":"Tenant"},
                  "priority":{"type":"integer","x-mcp-header":"Priority"},
                  "options":{"type":"object","properties":{
                    "dryRun":{"type":"boolean","x-mcp-header":"Dry-Run"}}}}}"""));

        assertThat(bindings).containsExactly(
                new ParamHeaderBinding("Mcp-Param-Tenant", List.of("tenant")),
                new ParamHeaderBinding("Mcp-Param-Priority", List.of("priority")),
                new ParamHeaderBinding("Mcp-Param-Dry-Run", List.of("options", "dryRun")));
    }

    @Test
    void bindsANullablePrimitive() {
        assertThat(ParamHeaderSchema.bindingsIn(json("""
                {"properties":{"region":{"type":["string","null"],"x-mcp-header":"Region"}}}""")))
                .containsExactly(new ParamHeaderBinding("Mcp-Param-Region", List.of("region")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"properties\":{\"a\":{\"type\":\"number\",\"x-mcp-header\":\"A\"}}}",
            "{\"properties\":{\"a\":{\"type\":\"object\",\"x-mcp-header\":\"A\"}}}",
            "{\"properties\":{\"a\":{\"type\":\"array\",\"x-mcp-header\":\"A\"}}}",
            "{\"properties\":{\"a\":{\"x-mcp-header\":\"A\"}}}",
            "{\"properties\":{\"a\":{\"type\":[\"string\",\"integer\"],\"x-mcp-header\":\"A\"}}}",
            "{\"properties\":{\"a\":{\"type\":\"string\",\"x-mcp-header\":\"Not Token\"}}}",
            "{\"properties\":{\"a\":{\"type\":\"string\",\"x-mcp-header\":\"\"}}}",
            "{\"properties\":{\"a\":{\"type\":\"string\",\"x-mcp-header\":7}}}",
            "{\"type\":\"string\",\"x-mcp-header\":\"Root\"}",
            "{\"properties\":{\"a\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"x-mcp-header\":\"A\"}}}}",
            "{\"allOf\":[{\"properties\":{\"a\":{\"type\":\"string\",\"x-mcp-header\":\"A\"}}}]}",
            "{\"anyOf\":[{\"properties\":{\"a\":{\"type\":\"string\",\"x-mcp-header\":\"A\"}}}]}",
            "{\"$defs\":{\"a\":{\"type\":\"string\",\"x-mcp-header\":\"A\"}}}",
            "{\"additionalProperties\":{\"type\":\"string\",\"x-mcp-header\":\"A\"}}",
            "{\"patternProperties\":{\"^a\":{\"type\":\"string\",\"x-mcp-header\":\"A\"}}}",
            "{\"if\":{\"properties\":{\"a\":{\"type\":\"string\",\"x-mcp-header\":\"A\"}}}}",
            "{\"properties\":{\"a\":{\"not\":{\"type\":\"string\",\"x-mcp-header\":\"A\"}}}}"})
    void skipsInvalidOrUnreachableAnnotations(String schema) {
        assertThat(ParamHeaderSchema.bindingsIn(json(schema))).isEmpty();
    }

    @Test
    void keepsOnlyTheFirstOfCaseInsensitivelyDuplicatedNames() {
        assertThat(ParamHeaderSchema.bindingsIn(json("""
                {"properties":{
                  "a":{"type":"string","x-mcp-header":"Tenant"},
                  "b":{"type":"string","x-mcp-header":"tenant"}}}""")))
                .containsExactly(new ParamHeaderBinding("Mcp-Param-Tenant", List.of("a")));
    }

    @Test
    void keepsValidSiblingsOfAnInvalidAnnotation() {
        assertThat(ParamHeaderSchema.bindingsIn(json("""
                {"properties":{
                  "bad":{"type":"number","x-mcp-header":"Bad"},
                  "good":{"type":"string","x-mcp-header":"Good"}}}""")))
                .containsExactly(new ParamHeaderBinding("Mcp-Param-Good", List.of("good")));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"properties\":{\"a\":{\"type\":\"string\",\"x-mcp-header\":\"Not Token\"}}} | 'x-mcp-header \"Not Token\" isn''t a valid header name'",
            "{\"properties\":{\"a\":{\"type\":\"string\",\"x-mcp-header\":7}}} | 'x-mcp-header 7 isn''t a valid header name'",
            "{\"properties\":{\"a\":{\"type\":\"number\",\"x-mcp-header\":\"A\"}}} | 'x-mcp-header A is on a, which isn''t a string, integer or boolean'",
            "{\"properties\":{\"o\":{\"properties\":{\"a\":{\"x-mcp-header\":\"A\"}}}}} | 'x-mcp-header A is on o.a, which isn''t a string, integer or boolean'",
            "{\"properties\":{\"a\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"x-mcp-header\":\"A\"}}}} | 'x-mcp-header A is on a value that isn''t reached through properties alone'",
            "{\"oneOf\":[{\"properties\":{\"a\":{\"type\":\"string\",\"x-mcp-header\":\"A\"}}}]} | 'x-mcp-header A is on a value that isn''t reached through properties alone'",
            "{\"properties\":{\"a\":{\"$ref\":\"#/$defs/t\"}},\"$defs\":{\"t\":{\"type\":\"string\",\"x-mcp-header\":\"A\"}}} | 'x-mcp-header A is on a value that isn''t reached through properties alone'",
            "{\"type\":\"string\",\"x-mcp-header\":\"Root\"} | 'x-mcp-header Root is on a value that isn''t reached through properties alone'",
            "{\"properties\":{\"a\":{\"type\":\"string\",\"x-mcp-header\":\"T\"},\"b\":{\"type\":\"string\",\"x-mcp-header\":\"t\"}}} | 'more than one x-mcp-header is named t'"})
    void explainsEachAnnotationItSkips(String schema, String problem) {
        assertThat(ParamHeaderSchema.problemsIn(json(schema))).containsExactly(problem);
    }

    @Test
    void findsNoProblemInValidAnnotations() {
        assertThat(ParamHeaderSchema.problemsIn(json(
                "{\"properties\":{\"tenant\":{\"type\":[\"string\",\"null\"],\"x-mcp-header\":\"Tenant\"},"
                        + "\"o\":{\"properties\":{\"dryRun\":{\"type\":\"boolean\",\"x-mcp-header\":\"Dry-Run\"}}}}}")))
                .isEmpty();
        assertThat(ParamHeaderSchema.problemsIn(json("null"))).isEmpty();
    }

    @Test
    void bindsNothingForAMissingOrNonObjectSchema() {
        assertThat(ParamHeaderSchema.bindingsIn(json("null"))).isEmpty();
        assertThat(ParamHeaderSchema.bindingsIn(json("\"text\""))).isEmpty();
        assertThat(ParamHeaderSchema.bindingsIn(json("{\"properties\":[]}"))).isEmpty();
    }

    private static JsonNode json(String text) {
        try {
            return McpObjectMapper.INSTANCE.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
