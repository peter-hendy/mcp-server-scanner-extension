package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record Leaf(List<Object> path, JsonNode value) {

    public static final String ARGUMENTS = "arguments";
    public static final String URI = "uri";

    public Leaf {
        path = List.copyOf(path);
    }

    public boolean named(String reference) {
        return name().equals(reference) || label().equals(reference);
    }

    public boolean resourceUri() {
        return path.equals(List.of(URI));
    }

    public String label() {
        return rendered(argumentPath());
    }

    public String pathText() {
        return rendered(path);
    }

    public String parentKey() {
        List<String> keys = path.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        return keys.size() < 2 ? null : keys.get(keys.size() - 2);
    }

    private static String rendered(List<Object> steps) {
        StringBuilder label = new StringBuilder();
        for (Object step : steps) {
            if (step instanceof Integer index) {
                label.append('[').append(index).append(']');
            } else {
                label.append(label.isEmpty() ? "" : ".").append(step);
            }
        }
        return label.toString();
    }

    public String name() {
        for (int index = path.size() - 1; index >= 0; index--) {
            if (path.get(index) instanceof String name) {
                return name;
            }
        }
        return "";
    }

    public String text() {
        return value.asText();
    }

    private List<Object> argumentPath() {
        return !path.isEmpty() && ARGUMENTS.equals(path.get(0)) ? path.subList(1, path.size()) : path;
    }
}
