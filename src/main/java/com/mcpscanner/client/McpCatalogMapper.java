package com.mcpscanner.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcpscanner.mcp.IconDescriptor;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.PromptArgument;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.mcp.ToolAnnotations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class McpCatalogMapper {
    private final ObjectMapper json = McpObjectMapper.INSTANCE;

    List<McpToolDefinition> tools(Map<String, Object> result) {
        return entries(result, "tools").stream().map(tool -> new McpToolDefinition(
                string(tool, "name"), string(tool, "description"), schema(tool.get("inputSchema")),
                icons(tool.get("icons")), annotations(tool.get("annotations")), raw(tool))).toList();
    }

    List<McpResourceDefinition> resources(Map<String, Object> result) {
        return entries(result, "resources").stream().map(resource -> new McpResourceDefinition(
                string(resource, "uri"), string(resource, "name"), string(resource, "description"),
                string(resource, "mimeType"), icons(resource.get("icons")), raw(resource))).toList();
    }

    List<McpResourceTemplateDefinition> templates(Map<String, Object> result) {
        return entries(result, "resourceTemplates").stream().map(template -> new McpResourceTemplateDefinition(
                string(template, "uriTemplate"), string(template, "name"), string(template, "description"),
                string(template, "mimeType"), icons(template.get("icons")), raw(template))).toList();
    }

    List<McpPromptDefinition> prompts(Map<String, Object> result) {
        return entries(result, "prompts").stream().map(prompt -> new McpPromptDefinition(
                string(prompt, "name"), string(prompt, "description"), promptArguments(prompt.get("arguments")),
                icons(prompt.get("icons")), raw(prompt))).toList();
    }

    ServerMetadata serverMetadata(Map<String, Object> envelope) {
        Map<String, Object> result = map(envelope.get("result"));
        Map<String, Object> meta = map(result.get("_meta"));
        if (meta.isEmpty()) {
            meta = map(envelope.get("_meta"));
        }
        Map<String, Object> serverInfo = map(meta.get("io.modelcontextprotocol/serverInfo"));
        if (serverInfo.isEmpty()) {
            serverInfo = map(result.get("serverInfo"));
        }
        Map<String, String> textInfo = new LinkedHashMap<>();
        serverInfo.forEach((key, value) -> {
            if (value instanceof String text) {
                textInfo.put(key, text);
            }
        });
        return new ServerMetadata(textInfo, string(result, "instructions") == null ? "" : string(result, "instructions"),
                map(result.get("capabilities")),
                icons(serverInfo.get("icons")), raw(result));
    }

    private JsonNode raw(Map<String, Object> entry) {
        return McpObjectMapper.RECEIVED_ORDER.valueToTree(entry);
    }

    private String schema(Object value) {
        try {
            return json.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid tool input schema", e);
        }
    }

    private static ToolAnnotations annotations(Object value) {
        Map<String, Object> fields = map(value);
        return new ToolAnnotations(string(fields, "title"), bool(fields, "readOnlyHint"),
                bool(fields, "destructiveHint"), bool(fields, "idempotentHint"), bool(fields, "openWorldHint"));
    }

    private static List<PromptArgument> promptArguments(Object value) {
        return maps(value).stream().map(argument -> new PromptArgument(
                string(argument, "name"), string(argument, "description"),
                Boolean.TRUE.equals(bool(argument, "required")))).toList();
    }

    private static List<IconDescriptor> icons(Object value) {
        return maps(value).stream().map(icon -> new IconDescriptor(
                string(icon, "src"), string(icon, "mimeType"), strings(icon.get("sizes")))).toList();
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        return items.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    static List<Map<String, Object>> entries(Map<String, Object> result, String key) {
        return maps(result.get(key));
    }

    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?>) {
                mapped.add(map(item));
            }
        }
        return mapped;
    }

    static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> mapped = new LinkedHashMap<>();
        source.forEach((key, field) -> {
            if (key instanceof String name) {
                mapped.put(name, field);
            }
        });
        return mapped;
    }

    static String string(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        return value instanceof String text ? text : null;
    }

    private static Boolean bool(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        return value instanceof Boolean flag ? flag : null;
    }
}
