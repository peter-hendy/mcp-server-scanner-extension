package com.mcpscanner.ui.sendto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;

import javax.swing.KeyStroke;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class SendToShortcuts {

    public static final String HOTKEYS_PATH = "user_options.misc.hotkeys";

    private static final String ACTION_PREFIX = "send_to_";
    private static final Map<String, String> BURP_DEFAULTS = Map.of(
            "send_to_repeater", "Ctrl+R",
            "send_to_intruder", "Ctrl+I",
            "send_to_organizer", "Ctrl+O");

    private final Supplier<String> userOptionsJson;

    public SendToShortcuts(Supplier<String> userOptionsJson) {
        this.userOptionsJson = Objects.requireNonNull(userOptionsJson);
    }

    public static SendToShortcuts defaults() {
        return new SendToShortcuts(() -> "");
    }

    public Optional<KeyStroke> keyStrokeFor(String destinationName) {
        String action = ACTION_PREFIX + destinationName.toLowerCase(Locale.ROOT);
        return BurpHotkey.parse(configuredHotkeys().getOrDefault(action, BURP_DEFAULTS.get(action)));
    }

    private Map<String, String> configuredHotkeys() {
        try {
            JsonNode hotkeys = McpObjectMapper.INSTANCE.readTree(userOptionsJson.get())
                    .path("user_options").path("misc").path("hotkeys");
            Map<String, String> byAction = new HashMap<>();
            hotkeys.forEach(entry -> byAction.put(entry.path("action").asText(), entry.path("hotkey").asText()));
            return byAction;
        } catch (RuntimeException | IOException unreadable) {
            return Map.of();
        }
    }
}
