package com.mcpscanner.ui.detail.uri;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.detail.FieldGrid;
import com.mcpscanner.ui.detail.JsonValueText;

import java.util.List;
import java.util.function.Function;
import java.util.stream.StreamSupport;

final class AnnotationFields {

    private AnnotationFields() {
    }

    static void addTo(FieldGrid grid, JsonNode annotations) {
        addAudience(grid, annotations.path("audience"));
        addIfPresent(grid, "Priority", annotations.path("priority"), PriorityText::of);
        addIfPresent(grid, "Last modified", annotations.path("lastModified"), LastModifiedText::of);
    }

    private static void addAudience(FieldGrid grid, JsonNode audience) {
        if (audience.isArray()) {
            grid.addComponent("Audience", ChipRow.of(roles(audience)));
        } else {
            addIfPresent(grid, "Audience", audience, JsonValueText::of);
        }
    }

    private static List<String> roles(JsonNode audience) {
        return StreamSupport.stream(audience.spliterator(), false)
                .map(role -> role.isTextual() ? role.asText() : JsonValueText.of(role))
                .toList();
    }

    private static void addIfPresent(FieldGrid grid, String label, JsonNode value,
                                     Function<JsonNode, String> describe) {
        if (!value.isMissingNode() && !value.isNull()) {
            grid.addText(label, describe.apply(value));
        }
    }
}
