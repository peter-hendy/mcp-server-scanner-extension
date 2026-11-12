package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.mcp.McpObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class TestTokens {

    static final String RS256_TOKEN = token("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"live-key\"}",
            "{\"iss\":\"https://as.example\",\"aud\":\"https://mcp.example/mcp\",\"sub\":\"alice\",\"exp\":4102444800}");
    static final String HS256_TOKEN = token("{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"real-kid\"}",
            "{\"sub\":\"alice\",\"exp\":4102444800}");

    private TestTokens() {}

    static ObjectNode object(String json) {
        try {
            return (ObjectNode) McpObjectMapper.INSTANCE.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static String token(String headerJson, String claimsJson) {
        return JwtSegments.encode(object(headerJson)) + "." + JwtSegments.encode(object(claimsJson)) + ".c2ln";
    }

    static ObjectNode headerOf(String token) {
        return decode(token.split("\\.", -1)[0]);
    }

    static ObjectNode claimsOf(String token) {
        return decode(token.split("\\.", -1)[1]);
    }

    static String signatureOf(String token) {
        return token.split("\\.", -1)[2];
    }

    static String signingInputOf(String token) {
        return token.substring(0, token.lastIndexOf('.'));
    }

    private static ObjectNode decode(String segment) {
        return object(new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8));
    }
}
