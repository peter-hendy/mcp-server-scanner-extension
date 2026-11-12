package com.mcpscanner.ui.detail;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

final class IconCaption {

    private static final String PART_SEPARATOR = " · ";
    private static final String SIZE_SEPARATOR = ", ";

    private IconCaption() {
    }

    static String of(JsonNode icon) {
        return Stream.of(sizes(icon.path("sizes")), icon.path("theme").asText(""), icon.path("mimeType").asText(""))
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(PART_SEPARATOR));
    }

    private static String sizes(JsonNode sizes) {
        return StreamSupport.stream(sizes.spliterator(), false)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .collect(Collectors.joining(SIZE_SEPARATOR));
    }
}
