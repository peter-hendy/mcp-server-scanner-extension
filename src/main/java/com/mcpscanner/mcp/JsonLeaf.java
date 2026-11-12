package com.mcpscanner.mcp;


import java.util.List;

public record JsonLeaf(List<JsonStep> steps, int startInclusive, int endExclusive, JsonValueKind kind,
                       String decodedValue) {

    public JsonLeaf {
        steps = List.copyOf(steps);
    }

    public String path() {
        return JsonStep.pathOf(steps);
    }

    public boolean isScalar() {
        return kind != JsonValueKind.OBJECT && kind != JsonValueKind.ARRAY;
    }
}
