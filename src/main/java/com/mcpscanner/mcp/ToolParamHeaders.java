package com.mcpscanner.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ToolParamHeaders {

    private final Consumer<String> warnings;
    private final Set<String> warnedTools = ConcurrentHashMap.newKeySet();
    private volatile Map<String, ParamHeaders> byTool = Map.of();

    public ToolParamHeaders(Consumer<String> warnings) {
        this.warnings = warnings;
    }

    public void learn(Collection<McpToolDefinition> tools) {
        Map<String, ParamHeaders> learned = new HashMap<>();
        tools.stream()
                .filter(tool -> tool.name() != null && !learned.containsKey(tool.name()))
                .forEach(tool -> learned.put(tool.name(), declaredBy(tool)));
        byTool = Map.copyOf(learned);
    }

    public void forget() {
        byTool = Map.of();
        warnedTools.clear();
    }

    public ParamHeaders forTool(String name) {
        return byTool.getOrDefault(name, ParamHeaders.NONE);
    }

    private ParamHeaders declaredBy(McpToolDefinition tool) {
        return parsed(tool.inputSchema()).map(schema -> {
            warnAboutSkippedAnnotations(tool.name(), ParamHeaderSchema.problemsIn(schema));
            return ParamHeaders.declaredBy(schema);
        }).orElse(ParamHeaders.NONE);
    }

    private void warnAboutSkippedAnnotations(String toolName, List<String> problems) {
        if (!problems.isEmpty() && warnedTools.add(toolName)) {
            warnings.accept("Tool " + toolName + ": Mcp-Param headers not sent for " + String.join("; ", problems));
        }
    }

    private static Optional<JsonNode> parsed(String inputSchema) {
        if (inputSchema == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(McpObjectMapper.INSTANCE.readTree(inputSchema));
        } catch (JsonProcessingException unparseable) {
            return Optional.empty();
        }
    }
}
