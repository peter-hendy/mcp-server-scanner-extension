package com.mcpscanner.cvetargets.manifest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.AccessItemKey;

import java.util.Map;

public record AccessItem(SeedRequest seed, AccessExpectation expectation, String protocolVersion,
                         Map<String, Map<String, GroundTruthClass>> groundTruth) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public AccessItem {
        groundTruth = groundTruth == null ? Map.of() : Map.copyOf(groundTruth);
    }

    public AccessItemKey key() {
        ObjectNode envelope = JSON.createObjectNode().put("jsonrpc", "2.0").put("id", 1).put("method", seed.method());
        envelope.set("params", seed.params());
        return AccessItemKey.fromRequestBody(envelope.toString())
                .orElseThrow(() -> new IllegalArgumentException("item seed names no tool or resource: " + envelope));
    }
}
