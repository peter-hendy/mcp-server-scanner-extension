package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.api.crossidentity.AbsentValues;
import com.mcpscanner.mcp.Leaf;
import com.mcpscanner.checks.api.crossidentity.OperationSeed;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

final class ForeignObject {

    private final OperationSeed seed;
    private final Map<Leaf, String> coordinates;

    private ForeignObject(OperationSeed seed, Map<Leaf, String> coordinates) {
        this.seed = seed;
        this.coordinates = coordinates;
    }

    static Optional<ForeignObject> locate(OperationSeed seed, Map<String, String> references) {
        Map<Leaf, String> coordinates = new LinkedHashMap<>();
        references.forEach((argument, value) -> seed.referenceLeaves(argument)
                .forEach(leaf -> coordinates.put(leaf, value)));
        boolean alreadyForeign = coordinates.entrySet().stream()
                .allMatch(entry -> entry.getKey().text().equals(entry.getValue()));
        return alreadyForeign ? Optional.empty() : Optional.of(new ForeignObject(seed, coordinates));
    }

    HttpRequest substitute(HttpRequest request) {
        Map<Leaf, JsonNode> values = new LinkedHashMap<>();
        coordinates.forEach((leaf, value) -> values.put(leaf, coordinate(leaf, value)));
        return seed.withLeaves(request, values);
    }

    HttpRequest absent(HttpRequest request, AbsentValues absent) {
        Map<Leaf, JsonNode> values = new LinkedHashMap<>();
        coordinates.keySet().forEach(leaf -> values.put(leaf, absent.forLeaf(leaf)));
        return seed.withLeaves(request, values);
    }

    private static JsonNode coordinate(Leaf leaf, String value) {
        return leaf.value().isIntegralNumber() && value.matches("-?\\d{1,18}")
                ? LongNode.valueOf(Long.parseLong(value)) : TextNode.valueOf(value);
    }

    String describe() {
        return coordinates.entrySet().stream().map(entry -> entry.getKey().name() + "=" + entry.getValue())
                .collect(Collectors.joining(", ", "the identity's owned object (", ")"));
    }
}
