package com.mcpscanner.checks.catalog.jwtvalidation;

import com.fasterxml.jackson.databind.node.ObjectNode;

record HmacSignature(String description, ObjectNode header, byte[] key) implements ForgedSignature {

    static final String RANDOM_SECRET_DESCRIPTION =
            "an HS256 token with the same header signed with a random HMAC secret (the HMAC signature is not "
                    + "verified for this key id)";

    @Override
    public String sign(ObjectNode claims) {
        UnsignedJwt unsigned = new UnsignedJwt(header, claims);
        return unsigned.withSignature(HmacSha256.digest(key, unsigned.signingInputBytes()));
    }
}
