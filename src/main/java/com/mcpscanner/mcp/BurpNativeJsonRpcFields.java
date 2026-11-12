package com.mcpscanner.mcp;

import burp.api.montoya.core.Version;

import java.util.Optional;
import java.util.function.Supplier;

public record BurpNativeJsonRpcFields(boolean present) {

    public static final long FIRST_NATIVE_BUILD = 20261100000055065L;

    public static final BurpNativeJsonRpcFields PRESENT = new BurpNativeJsonRpcFields(true);
    public static final BurpNativeJsonRpcFields ABSENT = new BurpNativeJsonRpcFields(false);

    public static BurpNativeJsonRpcFields detectedIn(Supplier<Version> version) {
        try {
            Version burp = version.get();
            return burp != null && burp.buildNumber() >= FIRST_NATIVE_BUILD ? PRESENT : ABSENT;
        } catch (RuntimeException unreadable) {
            return ABSENT;
        }
    }

    public Optional<BurpJsonRpcArguments> argumentsIn(byte[] body) {
        return present ? BurpJsonRpcArguments.in(body) : Optional.empty();
    }
}
