package com.mcpscanner.ui;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.Function;

record DetailSubject<T>(String noun, Function<T, String> nameOf, Function<T, String> descriptionOf,
                        Function<T, JsonNode> rawOf, String listMethod) {

    String rawHint() {
        return "The whole " + listMethod + " entry as the server sent it. Key order is kept.";
    }
}
