package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

record CompactJwt(ObjectNode header, ObjectNode claims) {

    private static final List<String> ASYMMETRIC_PREFIXES = List.of("RS", "PS", "ES", "EdDSA");

    static Optional<CompactJwt> parse(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return Optional.empty();
        }
        Optional<ObjectNode> header = JwtSegments.decodeObject(parts[0]);
        Optional<ObjectNode> claims = JwtSegments.decodeObject(parts[1]);
        if (header.isEmpty() || claims.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CompactJwt(header.get(), claims.get()));
    }

    String algorithm() {
        return header.path("alg").asText("");
    }

    boolean asymmetricallySigned() {
        String algorithm = algorithm();
        return ASYMMETRIC_PREFIXES.stream().anyMatch(algorithm::startsWith);
    }

    Optional<String> issuer() {
        JsonNode issuer = claims.path("iss");
        return issuer.isTextual() ? Optional.of(issuer.asText()) : Optional.empty();
    }
}
