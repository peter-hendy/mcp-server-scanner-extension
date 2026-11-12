package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

enum KnownCapability {
    TOOLS("tools", "Tools", FlagReading.of("listChanged", "list changed")),
    RESOURCES("resources", "Resources", FlagReading.of("subscribe", "subscribe", "listChanged", "list changed")),
    PROMPTS("prompts", "Prompts", FlagReading.of("listChanged", "list changed")),
    LOGGING("logging", "Logging", new SupportedReading()),
    COMPLETIONS("completions", "Completions", new SupportedReading()),
    TASKS("tasks", "Tasks", new LeafPathReading()),
    EXPERIMENTAL("experimental", "Experimental", new KeyReading()),
    EXTENSIONS("extensions", "Extensions", new ExtensionReading());

    static final Set<String> KEYS = Arrays.stream(values()).map(known -> known.key).collect(Collectors.toSet());

    private final String key;
    private final String title;
    private final CapabilityReading reading;

    KnownCapability(String key, String title, CapabilityReading reading) {
        this.key = key;
        this.title = title;
        this.reading = reading;
    }

    CapabilityCard cardFrom(JsonNode capabilities) {
        JsonNode capability = capabilities.path(key);
        if (capability.isMissingNode()) {
            return CapabilityCard.notAdvertised(title);
        }
        return CapabilityCard.advertised(title, linesOf(capability));
    }

    private List<CapabilityLine> linesOf(JsonNode capability) {
        return capability.isObject() ? reading.linesOf(capability) : List.of(CapabilityLine.value(capability.toString()));
    }
}
