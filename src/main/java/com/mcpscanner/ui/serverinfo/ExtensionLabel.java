package com.mcpscanner.ui.serverinfo;

import java.util.Map;
import java.util.Optional;

final class ExtensionLabel {

    private static final Map<String, String> KNOWN = Map.of(
            "io.modelcontextprotocol/tasks", "Tasks",
            "io.modelcontextprotocol/ui", "MCP Apps",
            "io.modelcontextprotocol/skills", "Skills",
            "io.modelcontextprotocol/server-card", "Server card");

    private ExtensionLabel() {
    }

    static Optional<String> of(String extensionKey) {
        return Optional.ofNullable(KNOWN.get(extensionKey));
    }
}
