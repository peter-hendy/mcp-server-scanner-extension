package com.mcpscanner.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ParamHeaders {

    public static final ParamHeaders NONE = new ParamHeaders(List.of());

    private final List<ParamHeaderBinding> bindings;

    public ParamHeaders(List<ParamHeaderBinding> bindings) {
        this.bindings = List.copyOf(bindings);
    }

    public static ParamHeaders declaredBy(JsonNode inputSchema) {
        return new ParamHeaders(ParamHeaderSchema.bindingsIn(inputSchema));
    }

    public Map<String, String> valuesFor(JsonNode arguments) {
        Map<String, String> values = new LinkedHashMap<>();
        if (arguments != null && arguments.isObject()) {
            bindings.forEach(binding -> binding.valueIn(arguments)
                    .ifPresent(value -> values.put(binding.headerName(), value)));
        }
        return values;
    }
}
