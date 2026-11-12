package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public final class SchemaDialect {

    private static final String DEFAULT_DIALECT_SINCE = "2025-11-25";
    private static final String VERSION_DEFAULT = "2020-12 (default)";
    private static final String UNSPECIFIED = "unspecified";
    private static final Map<String, String> KNOWN_META_SCHEMAS = Map.of(
            "json-schema.org/draft-04/schema", "draft-04",
            "json-schema.org/draft-06/schema", "draft-06",
            "json-schema.org/draft-07/schema", "draft-07",
            "json-schema.org/draft/2019-09/schema", "2019-09",
            "json-schema.org/draft/2020-12/schema", "2020-12");

    private SchemaDialect() {
    }

    public static String label(JsonNode schema, String protocolVersion) {
        JsonNode declared = schema == null ? null : schema.get("$schema");
        if (declared != null && declared.isTextual()) {
            return shortName(declared.asText());
        }
        return defaultsTo2020(protocolVersion) ? VERSION_DEFAULT : UNSPECIFIED;
    }

    private static String shortName(String uri) {
        return KNOWN_META_SCHEMAS.getOrDefault(withoutSchemeOrFragment(uri), uri);
    }

    private static String withoutSchemeOrFragment(String uri) {
        String stripped = uri.replaceFirst("^https?://", "");
        return stripped.endsWith("#") ? stripped.substring(0, stripped.length() - 1) : stripped;
    }

    private static boolean defaultsTo2020(String protocolVersion) {
        return protocolVersion != null && protocolVersion.compareTo(DEFAULT_DIALECT_SINCE) >= 0;
    }
}
