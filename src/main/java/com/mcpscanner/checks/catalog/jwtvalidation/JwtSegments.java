package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;

import java.io.IOException;
import java.util.Base64;
import java.util.Optional;

final class JwtSegments {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private JwtSegments() {}

    static String encode(JsonNode value) {
        try {
            return encode(McpObjectMapper.INSTANCE.writeValueAsBytes(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise JWT segment", e);
        }
    }

    static String encode(byte[] bytes) {
        return ENCODER.encodeToString(bytes);
    }

    static Optional<ObjectNode> decodeObject(String segment) {
        try {
            JsonNode node = McpObjectMapper.INSTANCE.readTree(DECODER.decode(segment));
            return node instanceof ObjectNode object ? Optional.of(object) : Optional.empty();
        } catch (IllegalArgumentException | IOException e) {
            return Optional.empty();
        }
    }
}
