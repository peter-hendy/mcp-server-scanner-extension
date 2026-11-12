package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static com.mcpscanner.ui.detail.schema.SchemaFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

class SchemaOutlineTest {

    @Test
    void petersSchemaGivesOneRequiredUuidRowWithLabelledPattern() {
        SchemaOutline outline = SchemaOutline.of(json(SchemaFixtures.PETERS_SCHEMA));

        assertThat(outline.rows()).hasSize(1);
        SchemaRow id = outline.rows().get(0);
        assertThat(id.depth()).isZero();
        assertThat(id.name()).isEqualTo("id");
        assertThat(id.required()).isTrue();
        assertThat(id.type()).isEqualTo("string");
        assertThat(id.format()).isEqualTo("uuid");
        assertThat(id.constraints()).containsExactly(SchemaConstraint.block("pattern", SchemaFixtures.UUID_PATTERN));
        assertThat(id.description()).isNull();
        assertThat(id.hasChildren()).isFalse();
        assertThat(outline.otherKeywords()).isEmpty();
    }

    @Test
    void petersSchemaSummary() {
        SchemaSummary summary = SchemaOutline.of(json(SchemaFixtures.PETERS_SCHEMA)).summary();

        assertThat(summary.parameterCount()).isEqualTo(1);
        assertThat(summary.requiredCount()).isEqualTo(1);
        assertThat(summary.text()).isEqualTo("1 parameter · 1 required · no extra properties rule");
    }

    @Test
    void nestedSchemaRowsInDeclaredOrderWithDepths() {
        List<SchemaRow> rows = SchemaOutline.of(json(SchemaFixtures.NESTED_SCHEMA)).rows();

        assertThat(rows).extracting(SchemaRow::name)
                .containsExactly("query", "state", "limit", "filters", "labels", "assignee", "created", "after");
        assertThat(rows).extracting(SchemaRow::depth).containsExactly(0, 0, 0, 0, 1, 1, 1, 2);
        assertThat(rows).extracting(SchemaRow::hasChildren)
                .containsExactly(false, false, false, true, false, false, true, false);
    }

    @Test
    void nestedSchemaScalarRows() {
        List<SchemaRow> rows = SchemaOutline.of(json(SchemaFixtures.NESTED_SCHEMA)).rows();

        SchemaRow query = rows.get(0);
        assertThat(query.required()).isTrue();
        assertThat(query.type()).isEqualTo("string");
        assertThat(query.constraints()).containsExactly(SchemaConstraint.inline("length", "1–256"));
        assertThat(query.description()).isEqualTo("Free-text search");

        SchemaRow state = rows.get(1);
        assertThat(state.required()).isFalse();
        assertThat(state.enumValues()).containsExactly("open", "closed", "all");
        assertThat(state.defaultValue()).isEqualTo("\"open\"");

        SchemaRow limit = rows.get(2);
        assertThat(limit.type()).isEqualTo("integer");
        assertThat(limit.constraints()).containsExactly(SchemaConstraint.inline("range", "1–100"));
        assertThat(limit.defaultValue()).isEqualTo("20");
        assertThat(limit.description()).isNull();
    }

    @Test
    void nestedSchemaObjectArrayUnionAndRefRows() {
        List<SchemaRow> rows = SchemaOutline.of(json(SchemaFixtures.NESTED_SCHEMA)).rows();

        assertThat(rows.get(3).type()).isEqualTo("object");
        assertThat(rows.get(3).description()).isEqualTo("Narrow results");

        SchemaRow labels = rows.get(4);
        assertThat(labels.type()).isEqualTo("array of string");
        assertThat(labels.constraints()).containsExactly(
                SchemaConstraint.inline("items", "≤ 10"), SchemaConstraint.flag("unique"));

        SchemaRow assignee = rows.get(5);
        assertThat(assignee.type()).isEqualTo("string | null");
        assertThat(assignee.constraints()).containsExactly(SchemaConstraint.flag("anyOf, 2 branches"));

        SchemaRow created = rows.get(6);
        assertThat(created.type()).isEqualTo("object");
        assertThat(created.refSource()).isEqualTo("$defs/DateRange");

        SchemaRow after = rows.get(7);
        assertThat(after.type()).isEqualTo("string");
        assertThat(after.format()).isEqualTo("date-time");
    }

    @Test
    void nestedSchemaSummaryAndOtherKeywords() {
        SchemaOutline outline = SchemaOutline.of(json(SchemaFixtures.NESTED_SCHEMA));

        assertThat(outline.summary().text())
                .isEqualTo("4 parameters · 1 required · additional properties: not allowed");
        assertThat(outline.otherKeywords()).containsExactly("x-internal: true", "filters.deprecated: true");
    }

    @Test
    void additionalPropertiesSchemaAndTrueAreDescribed() {
        assertThat(summaryOf("{\"type\":\"object\",\"additionalProperties\":{\"type\":\"string\"}}"))
                .endsWith("additional properties: allowed (string)");
        assertThat(summaryOf("{\"type\":\"object\",\"additionalProperties\":true}"))
                .endsWith("additional properties: allowed");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"object\"}",
            "{\"type\":\"object\",\"properties\":{}}",
            "[1,2]",
            "\"just a string\"",
            "{\"type\":\"object\",\"properties\":[1]}",
            "{\"type\":\"object\",\"properties\":{\"a\":{}},\"required\":\"a\"}"
    })
    void minimalOrMalformedSchemasNeverThrow(String schema) {
        SchemaOutline outline = SchemaOutline.of(json(schema));

        assertThat(outline.summary().requiredCount()).isZero();
        assertThat(outline.rows()).allSatisfy(row -> assertThat(row.required()).isFalse());
    }

    @Test
    void emptyObjectSchemaHasNoRows() {
        SchemaOutline outline = SchemaOutline.of(json("{\"type\":\"object\"}"));

        assertThat(outline.isEmpty()).isTrue();
        assertThat(outline.parameterCount()).isZero();
        assertThat(outline.summary().text()).isEqualTo("0 parameters · 0 required · no extra properties rule");
        assertThat(SchemaOutline.of(null).isEmpty()).isTrue();
    }

    @Test
    void draft07DefinitionsRefIsResolvedWithEscapedPointers() {
        List<SchemaRow> rows = SchemaOutline.of(json(SchemaFixtures.DEFINITIONS_SCHEMA)).rows();

        assertThat(rows).extracting(SchemaRow::name).containsExactly("owner", "name", "slashed");
        assertThat(rows.get(0).refSource()).isEqualTo("definitions/Person");
        assertThat(rows.get(0).description()).isEqualTo("Who owns it");
        assertThat(rows.get(1).required()).isTrue();
        assertThat(rows.get(2).type()).isEqualTo("integer");
    }

    @Test
    void cyclicRefTerminatesAndIsLabelled() {
        List<SchemaRow> rows = SchemaOutline.of(json(SchemaFixtures.CYCLIC_SCHEMA)).rows();

        assertThat(rows).extracting(SchemaRow::name).containsExactly("root", "child");
        assertThat(rows.get(1).refSource()).isEqualTo("$defs/Node");
        assertThat(rows.get(1).hasChildren()).isFalse();
        assertThat(rows.get(1).constraints()).contains(SchemaConstraint.flag("recursive, not expanded"));
    }

    @Test
    void selfReferencingRefChainIsLabelledUnresolved() {
        SchemaRow row = SchemaOutline.of(json(
                "{\"properties\":{\"a\":{\"$ref\":\"#/$defs/A\"}},\"$defs\":{\"A\":{\"$ref\":\"#/$defs/A\"}}}"))
                .rows().get(0);

        assertThat(row.constraints()).contains(SchemaConstraint.inline("unresolved $ref", "#/$defs/A"));
    }

    @Test
    void deepSchemaTerminatesAndIsLabelled() {
        List<SchemaRow> rows = SchemaOutline.of(SchemaFixtures.deeplyNested(200)).rows();

        assertThat(rows).hasSizeLessThan(20);
        assertThat(rows.get(rows.size() - 1).constraints()).contains(SchemaConstraint.flag("deeper nesting not shown"));
    }

    @Test
    void unresolvableRefIsLabelledNotThrown() {
        SchemaRow row = SchemaOutline.of(json(
                "{\"properties\":{\"a\":{\"$ref\":\"#/$defs/Missing\"},\"b\":{\"$ref\":\"https://x.test/s.json\"}}}"))
                .rows().get(0);

        assertThat(row.refSource()).isEqualTo("$defs/Missing");
        assertThat(row.constraints()).contains(SchemaConstraint.inline("unresolved $ref", "#/$defs/Missing"));
    }

    @Test
    void typeArraysAndInferredTypes() {
        List<SchemaRow> rows = SchemaOutline.of(json("""
                {"properties": {
                  "a": {"type": ["string", "null"]},
                  "b": {"enum": ["x", "y"]},
                  "c": {"const": 3},
                  "d": {"enum": ["x", 1]},
                  "e": {"description": "anything"},
                  "f": {"oneOf": [{"type": "integer"}, {"type": "string"}], "const": "z"}
                }}
                """)).rows();

        assertThat(rows).extracting(SchemaRow::type)
                .containsExactly("string | null", "string", "integer", "", "", "integer | string");
        assertThat(rows.get(2).constraints()).containsExactly(SchemaConstraint.inline("const", "3"));
        assertThat(rows.get(5).constraints()).contains(SchemaConstraint.flag("oneOf, 2 branches"));
    }

    @Test
    void numericAndCountConstraintVariants() {
        List<SchemaRow> rows = SchemaOutline.of(json("""
                {"properties": {
                  "a": {"type": "number", "exclusiveMinimum": 0, "maximum": 1, "multipleOf": 0.5},
                  "b": {"type": "string", "minLength": 3},
                  "c": {"type": "number", "minimum": 0, "exclusiveMinimum": true},
                  "d": {"type": "object", "minProperties": 1, "maxProperties": 4, "allOf": [{}]}
                }}
                """)).rows();

        assertThat(rows.get(0).constraints()).containsExactly(
                SchemaConstraint.inline("range", "> 0, ≤ 1"), SchemaConstraint.inline("multiple of", "0.5"));
        assertThat(rows.get(1).constraints()).containsExactly(SchemaConstraint.inline("length", "≥ 3"));
        assertThat(rows.get(2).constraints()).containsExactly(SchemaConstraint.inline("range", "> 0"));
        assertThat(rows.get(3).constraints()).containsExactly(
                SchemaConstraint.inline("properties", "1–4"), SchemaConstraint.flag("allOf, 1 branch"));
    }

    @Test
    void arrayItemObjectsAndPrefixItemsBecomeChildRows() {
        List<SchemaRow> rows = SchemaOutline.of(json("""
                {"properties": {
                  "people": {"type": "array", "items": {"type": "object", "required": ["n"],
                             "properties": {"n": {"type": "string"}}}},
                  "pair": {"type": "array", "prefixItems": [{"type": "string"}, {"type": "integer"}]}
                }}
                """)).rows();

        assertThat(rows).extracting(SchemaRow::name).containsExactly("people", "n", "pair", "[0]", "[1]");
        assertThat(rows).extracting(SchemaRow::depth).containsExactly(0, 1, 0, 1, 1);
        assertThat(rows.get(0).type()).isEqualTo("array of object");
        assertThat(rows.get(1).required()).isTrue();
        assertThat(rows.get(4).type()).isEqualTo("integer");
    }

    @Test
    void unknownKeywordsAnywhereAreReportedWithPaths() {
        List<String> other = SchemaOutline.of(json("""
                {"title": "Args", "if": {"required": ["a"]}, "then": {}, "patternProperties": {"^x": {}},
                 "dependentRequired": {"a": ["b"]}, "x-meta": {"z": 1, "a": {"y": 2, "b": 3}},
                 "properties": {
                   "a": {"type": "string", "examples": ["e"], "title": "A"},
                   "list": {"type": "array", "items": {"type": "string", "x-tag": 1}},
                   "u": {"anyOf": [{"type": "string", "minLength": 2}, {"type": "null"}]}
                 }}
                """)).otherKeywords();

        assertThat(other).containsExactly(
                "title: \"Args\"",
                "if: {\"required\":[\"a\"]}",
                "then: {}",
                "patternProperties: {\"^x\":{}}",
                "dependentRequired: {\"a\":[\"b\"]}",
                "x-meta: {\"z\":1,\"a\":{\"y\":2,\"b\":3}}",
                "a.examples: [\"e\"]",
                "a.title: \"A\"",
                "list[].x-tag: 1",
                "u.anyOf[0].minLength: 2");
    }

    @Test
    void htmlLookingDescriptionIsKeptLiterally() {
        SchemaRow row = SchemaOutline.of(json(
                "{\"properties\":{\"a\":{\"description\":\"<html><img src=x></html>\"}}}")).rows().get(0);

        assertThat(row.description()).isEqualTo("<html><img src=x></html>");
    }

    @Test
    void wholeDecimalValuesAreLabelledInteger() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("choice").putArray("enum").add(decimal("1")).add(decimal("2.00"));
        properties.putObject("fixed").set("const", decimal("3"));

        assertThat(SchemaOutline.of(schema).rows()).extracting(SchemaRow::type).containsExactly("integer", "integer");
    }

    @Test
    void fractionalDecimalValuesStayNumber() {
        ObjectNode schema = JsonNodeFactory.instance.objectNode();
        schema.putObject("properties").putObject("ratio").set("const", decimal("1.5"));

        assertThat(SchemaOutline.of(schema).rows().get(0).type()).isEqualTo("number");
    }

    private static DecimalNode decimal(String value) {
        return DecimalNode.valueOf(new BigDecimal(value));
    }

    private static String summaryOf(String schema) {
        return SchemaOutline.of(json(schema)).summary().text();
    }
}
