package com.mcpscanner.cvetargets.environment;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ArmServices {

    private ArmServices() {
    }

    static Set<String> helpersOf(JsonNode services, String armService) {
        Set<String> helpers = new LinkedHashSet<>(startedWith(services, armService));
        helpers.remove(armService);
        return helpers;
    }

    static Set<String> startedWith(JsonNode services, String armService) {
        Set<String> started = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(List.of(armService));
        while (!pending.isEmpty()) {
            String service = pending.pop();
            if (started.add(service)) {
                services.path(service).path("depends_on").fieldNames().forEachRemaining(pending::push);
            }
        }
        return started;
    }
}
