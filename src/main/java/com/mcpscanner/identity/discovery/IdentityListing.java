package com.mcpscanner.identity.discovery;

import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.mcp.CatalogItem;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record IdentityListing(Map<String, McpToolDefinition> tools, Map<String, McpResourceDefinition> resources,
                              Set<String> templateUris, Set<String> promptNames) {

    public IdentityListing {
        tools = Collections.unmodifiableMap(new LinkedHashMap<>(tools));
        resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
        templateUris = Set.copyOf(templateUris);
        promptNames = Set.copyOf(promptNames);
    }

    public static IdentityListing of(ConnectResult catalog) {
        return new IdentityListing(
                byKey(CatalogItem.wellFormed(catalog.tools()), McpToolDefinition::name),
                byKey(CatalogItem.wellFormed(catalog.resources()), McpResourceDefinition::uri),
                keys(CatalogItem.wellFormed(catalog.resourceTemplates()), McpResourceTemplateDefinition::uriTemplate),
                keys(CatalogItem.wellFormed(catalog.prompts()), McpPromptDefinition::name));
    }

    public Set<String> toolNames() {
        return tools.keySet();
    }

    public Set<String> resourceUris() {
        return resources.keySet();
    }

    public boolean lists(AccessItemKey item) {
        return switch (item.kind()) {
            case TOOL -> tools.containsKey(item.name());
            case RESOURCE -> resources.containsKey(item.name());
        };
    }

    private static <T> Map<String, T> byKey(List<T> definitions, Function<T, String> key) {
        Map<String, T> byKey = new LinkedHashMap<>();
        definitions.forEach(definition -> byKey.putIfAbsent(key.apply(definition), definition));
        return byKey;
    }

    private static <T> Set<String> keys(Collection<T> definitions, Function<T, String> key) {
        return definitions.stream().map(key).collect(Collectors.toUnmodifiableSet());
    }
}
