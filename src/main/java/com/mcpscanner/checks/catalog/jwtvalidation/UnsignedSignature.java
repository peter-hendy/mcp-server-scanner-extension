package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.node.ObjectNode;

final class UnsignedSignature implements ForgedSignature {

    private final ObjectNode header;

    UnsignedSignature(CompactJwt captured) {
        this.header = ForgedHeader.from(captured, "none");
    }

    @Override
    public String description() {
        return "an unsigned token (alg: none, empty signature)";
    }

    @Override
    public String sign(ObjectNode claims) {
        return new UnsignedJwt(header, claims).signingInput() + ".";
    }
}
