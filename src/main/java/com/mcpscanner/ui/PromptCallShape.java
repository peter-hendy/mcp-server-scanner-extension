package com.mcpscanner.ui;

import com.fasterxml.jackson.databind.node.TextNode;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

final class PromptCallShape {

    private static final String METHOD = "prompts/get  ";
    private static final String OPTIONAL_MARK = "?";

    private PromptCallShape() {
    }

    static String of(String promptName, List<PromptArgumentEntry> entries) {
        return METHOD + "name=" + TextNode.valueOf(Objects.toString(promptName, "")) + "\n"
                + " ".repeat(METHOD.length()) + "arguments={ " + argumentList(entries) + " }";
    }

    private static String argumentList(List<PromptArgumentEntry> entries) {
        return entries.stream()
                .map(entry -> entry.name() + (entry.argument().required() ? "" : OPTIONAL_MARK))
                .collect(Collectors.joining(", "));
    }
}
