package com.mcpscanner.checks.shared.random;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class RandomHex {

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomHex() {
    }

    public static String of(int byteCount) {
        byte[] value = new byte[byteCount];
        RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }
}
