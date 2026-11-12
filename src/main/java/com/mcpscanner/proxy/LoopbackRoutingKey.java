package com.mcpscanner.proxy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class LoopbackRoutingKey {

    private static final int KEY_BYTES = 32;

    private final String value;

    private LoopbackRoutingKey(String value) {
        this.value = value;
    }

    public static LoopbackRoutingKey mint() {
        byte[] bytes = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(bytes);
        return new LoopbackRoutingKey(HexFormat.of().formatHex(bytes));
    }

    public String value() {
        return value;
    }

    public boolean admits(String presented) {
        return presented != null && MessageDigest.isEqual(bytesOf(value), bytesOf(presented));
    }

    private static byte[] bytesOf(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
