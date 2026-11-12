package com.mcpscanner.mcp;

import java.util.List;

public sealed interface JsonStep {

    String appendedTo(String path);

    record Member(String name) implements JsonStep {
        @Override
        public String appendedTo(String path) {
            return path.isEmpty() ? name : path + "." + name;
        }
    }

    record Item(int index) implements JsonStep {
        @Override
        public String appendedTo(String path) {
            return path + "[" + index + "]";
        }
    }

    static String pathOf(List<JsonStep> steps) {
        String path = "";
        for (JsonStep step : steps) {
            path = step.appendedTo(path);
        }
        return path;
    }
}
