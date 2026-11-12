package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;

record UnsignedJwt(ObjectNode header, ObjectNode claims) {

    String signingInput() {
        return JwtSegments.encode(header) + "." + JwtSegments.encode(claims);
    }

    byte[] signingInputBytes() {
        return signingInput().getBytes(StandardCharsets.US_ASCII);
    }

    String withSignature(byte[] signature) {
        return signingInput() + "." + JwtSegments.encode(signature);
    }
}
