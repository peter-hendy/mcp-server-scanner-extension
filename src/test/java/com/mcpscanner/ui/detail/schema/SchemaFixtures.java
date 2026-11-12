package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;

final class SchemaFixtures {

    static final String UUID_PATTERN = "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}"
            + "-[0-9a-fA-F]{12}|00000000-0000-0000-0000-000000000000|ffffffff-ffff-ffff-ffff-ffffffffffff)$";

    static final String PETERS_SCHEMA = "{\"$schema\":\"http://json-schema.org/draft-07/schema#\",\"properties\":"
            + "{\"id\":{\"format\":\"uuid\",\"pattern\":\"" + UUID_PATTERN.replace("\\", "\\\\") + "\",\"type\":\"string\"}},"
            + "\"required\":[\"id\"],\"type\":\"object\"}";

    static final String NESTED_SCHEMA = """
            {
              "type": "object",
              "x-internal": true,
              "additionalProperties": false,
              "required": ["query"],
              "properties": {
                "query": {"type": "string", "minLength": 1, "maxLength": 256, "description": "Free-text search"},
                "state": {"type": "string", "enum": ["open", "closed", "all"], "default": "open",
                          "description": "Issue state"},
                "limit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 20},
                "filters": {
                  "type": "object",
                  "description": "Narrow results",
                  "deprecated": true,
                  "properties": {
                    "labels": {"type": "array", "items": {"type": "string"}, "maxItems": 10, "uniqueItems": true},
                    "assignee": {"anyOf": [{"type": "string"}, {"type": "null"}], "description": "Login or null"},
                    "created": {"$ref": "#/$defs/DateRange"}
                  }
                }
              },
              "$defs": {
                "DateRange": {"type": "object", "properties": {"after": {"type": "string", "format": "date-time"}}}
              }
            }
            """;

    static final String DEFINITIONS_SCHEMA = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "owner": {"$ref": "#/definitions/Person", "description": "Who owns it"},
                "slashed": {"$ref": "#/definitions/a~1b~0c"}
              },
              "definitions": {
                "Person": {"type": "object", "required": ["name"], "properties": {"name": {"type": "string"}}},
                "a/b~c": {"type": "integer"}
              }
            }
            """;

    static final String CYCLIC_SCHEMA = """
            {
              "type": "object",
              "properties": {"root": {"$ref": "#/$defs/Node"}},
              "$defs": {
                "Node": {"type": "object", "properties": {"child": {"$ref": "#/$defs/Node"}}}
              }
            }
            """;

    private SchemaFixtures() {
    }

    static JsonNode json(String text) {
        try {
            return McpObjectMapper.INSTANCE.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static JsonNode deeplyNested(int levels) {
        ObjectNode root = JsonNodeFactory.instance.objectNode().put("type", "object");
        ObjectNode current = root;
        for (int level = 0; level < levels; level++) {
            ObjectNode child = JsonNodeFactory.instance.objectNode().put("type", "object");
            current.putObject("properties").set("level" + level, child);
            current = child;
        }
        return root;
    }

    static JsonNode propertyFanOut(int fan, int definitions) {
        ObjectNode root = JsonNodeFactory.instance.objectNode().put("type", "object");
        ObjectNode defs = root.putObject("$defs");
        for (int index = 0; index < definitions; index++) {
            defs.set("D" + index, fannedProperties(fan, "#/$defs/D" + (index + 1)));
        }
        root.setAll(fannedProperties(fan, "#/$defs/D0"));
        return root;
    }

    static JsonNode unionFanOut(int branches, int definitions) {
        ObjectNode root = JsonNodeFactory.instance.objectNode().put("type", "object");
        ObjectNode defs = root.putObject("$defs");
        for (int index = 0; index < definitions; index++) {
            ObjectNode definition = JsonNodeFactory.instance.objectNode();
            for (int branch = 0; branch < branches; branch++) {
                definition.withArray("anyOf").add(reference("#/$defs/D" + (index + 1)));
            }
            defs.set("D" + index, definition);
        }
        root.putObject("properties").set("value", reference("#/$defs/D0"));
        return root;
    }

    private static ObjectNode fannedProperties(int fan, String ref) {
        ObjectNode schema = JsonNodeFactory.instance.objectNode().put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        for (int index = 0; index < fan; index++) {
            properties.set("p" + index, reference(ref));
        }
        return schema;
    }

    private static ObjectNode reference(String ref) {
        return JsonNodeFactory.instance.objectNode().put("$ref", ref);
    }
}
