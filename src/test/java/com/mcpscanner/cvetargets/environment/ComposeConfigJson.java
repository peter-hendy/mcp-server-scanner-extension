package com.mcpscanner.cvetargets.environment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ComposeConfigJson {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ComposeConfigJson() {
    }

    static JsonNode parse(String resolvedComposeJson) {
        try {
            return JSON.readTree(resolvedComposeJson);
        } catch (JsonProcessingException e) {
            throw new EnvironmentException("docker compose config returned invalid JSON: " + e.getOriginalMessage());
        }
    }
}
