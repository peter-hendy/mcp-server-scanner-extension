package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;

import java.util.List;

final class ForgedHeader {

    private static final List<String> KEY_SELECTORS = List.of("kid", "x5t", "x5t#S256");
    private static final String DEFAULT_TYPE = "JWT";

    private ForgedHeader() {}

    static ObjectNode from(CompactJwt captured, String algorithm) {
        ObjectNode header = base(algorithm, captured.header().path("typ").asText(DEFAULT_TYPE));
        for (String selector : KEY_SELECTORS) {
            JsonNode value = captured.header().path(selector);
            if (value.isTextual()) {
                header.put(selector, value.asText());
            }
        }
        return header;
    }

    static ObjectNode withKeyId(String algorithm, String keyId) {
        return base(algorithm, DEFAULT_TYPE).put("kid", keyId);
    }

    private static ObjectNode base(String algorithm, String type) {
        return McpObjectMapper.INSTANCE.createObjectNode().put("alg", algorithm).put("typ", type);
    }
}
