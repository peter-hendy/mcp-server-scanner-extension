package com.mcpscanner.checks.catalog.jwtvalidation;

import java.security.SecureRandom;

final class RandomSecret {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32;

    private RandomSecret() {}

    static byte[] bytes() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return secret;
    }
}
