package com.mcpscanner.ui.detail.schema;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.Set;

final class RequiredNames {

    private RequiredNames() {
    }

    static Set<String> of(JsonNode schema) {
        Set<String> names = new LinkedHashSet<>();
        JsonText.elements(schema.path("required")).stream()
                .filter(JsonNode::isTextual)
                .forEach(name -> names.add(name.asText()));
        return names;
    }
}
