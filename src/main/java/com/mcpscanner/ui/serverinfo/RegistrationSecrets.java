package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

final class RegistrationSecrets {

    static final String MASK = "••••••••";

    private static final Set<String> SECRET_FIELDS = Set.of(
            "client_secret", "registration_access_token", "access_token", "refresh_token", "id_token");

    private RegistrationSecrets() {
    }

    static JsonNode masked(JsonNode document) {
        JsonNode copy = document.deepCopy();
        mask(copy);
        return copy;
    }

    private static void mask(JsonNode node) {
        if (node instanceof ObjectNode object) {
            object.fieldNames().forEachRemaining(name -> {
                if (SECRET_FIELDS.contains(name)) {
                    object.put(name, MASK);
                }
            });
        }
        node.elements().forEachRemaining(RegistrationSecrets::mask);
    }
}
