package com.mcpscanner.ui;

import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.mcp.McpToolDefinition;

import java.util.Optional;
import java.util.function.Supplier;

final class ToolSelectionPreferences {

    private final ExtensionConfigStore configStore;
    private final Supplier<String> endpointSupplier;

    ToolSelectionPreferences(ExtensionConfigStore configStore, Supplier<String> endpointSupplier) {
        this.configStore = configStore;
        this.endpointSupplier = endpointSupplier != null ? endpointSupplier : () -> null;
    }

    static ToolSelectionPreferences none() {
        return new ToolSelectionPreferences(null, null);
    }

    boolean isConfigured() {
        return configStore != null;
    }

    boolean defaultSelection(McpToolDefinition tool) {
        return endpoint()
                .map(endpoint -> configStore.toolSelected(endpoint, tool.name()))
                .orElseGet(() -> tool.annotations().isExplicitlyReadOnly());
    }

    void remember(McpToolDefinition tool, boolean selected) {
        endpoint().ifPresent(endpoint -> configStore.setToolSelected(endpoint, tool.name(), selected));
    }

    private Optional<String> endpoint() {
        if (configStore == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(endpointSupplier.get()).filter(endpoint -> !endpoint.isEmpty());
    }
}
