package com.mcpscanner.client;

import com.mcpscanner.config.RedirectMode;
import com.mcpscanner.config.ResourceCap;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

public record ScannerSessionSettings(Supplier<RedirectMode> redirectMode,
                                     BooleanSupplier answerServerPings,
                                     BooleanSupplier useBurpNetworking,
                                     ToLongFunction<ResourceCap> resourceCaps) {

    public ScannerSessionSettings {
        Objects.requireNonNull(redirectMode, "redirectMode");
        Objects.requireNonNull(answerServerPings, "answerServerPings");
        Objects.requireNonNull(useBurpNetworking, "useBurpNetworking");
        Objects.requireNonNull(resourceCaps, "resourceCaps");
    }

    public static ScannerSessionSettings defaults() {
        return new ScannerSessionSettings(() -> RedirectMode.METHOD_PRESERVING_SAME_ORIGIN, () -> true, () -> true, ResourceCap::defaultValue);
    }

    void applyTo(McpScannerSession session) {
        session.setRedirectModeSupplier(redirectMode);
        session.setServerPingsEnabled(answerServerPings);
        session.setUseBurpNetworking(useBurpNetworking);
    }
}
