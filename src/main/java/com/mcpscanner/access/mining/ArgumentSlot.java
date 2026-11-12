package com.mcpscanner.access.mining;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collections;
import java.util.Set;

record ArgumentSlot(String name, Set<String> aliases, SlotType type) {

    ArgumentSlot {
        aliases = Set.copyOf(aliases);
    }

    boolean takes(Set<String> valueAliases, JsonNode value) {
        return !Collections.disjoint(aliases, valueAliases) && type.accepts(value);
    }
}
