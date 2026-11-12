package com.mcpscanner.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.mcpscanner.mcp.PromptArgument;
import com.mcpscanner.ui.detail.DeclaredTitle;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

record PromptArgumentEntry(PromptArgument argument, Optional<String> title) {

    static List<PromptArgumentEntry> listOf(List<PromptArgument> arguments, JsonNode declaredArguments) {
        Map<String, JsonNode> declared = declaredByName(declaredArguments);
        return arguments.stream()
                .map(argument -> new PromptArgumentEntry(argument, titleOf(declared.get(argument.name()))))
                .toList();
    }

    String name() {
        return argument.name() == null ? "" : argument.name();
    }

    private static Optional<String> titleOf(JsonNode declaration) {
        return DeclaredTitle.firstOf((declaration == null ? MissingNode.getInstance() : declaration).path("title"));
    }

    private static Map<String, JsonNode> declaredByName(JsonNode declaredArguments) {
        return StreamSupport.stream(declaredArguments.spliterator(), false)
                .filter(declaration -> declaration.path("name").isTextual())
                .collect(Collectors.toMap(declaration -> declaration.path("name").asText(), declaration -> declaration,
                        (first, ignored) -> first));
    }
}
