package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.handler.HttpRequestToBeSent;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * {@link SwapPolicy} that switches behaviour on the live MCP-proxy toggle.
 *
 * <p>When the toggle is OFF it delegates to {@link SwapAllMatchingTools} — the pre-feature behaviour,
 * which swaps every matching request and deliberately reads no {@code toolSource()} — so the handler
 * stays byte-identical to before the feature existed. When the toggle is ON it delegates to
 * {@link ScannerFamilySwapPolicy}, narrowing the swap to scanner-family traffic so live Proxy
 * observation can flow through un-swapped.
 *
 * <p>The toggle is read live on every call so a runtime flip takes effect without reconstructing the
 * handler.
 */
public final class ToggleAwareSwapPolicy implements SwapPolicy {

    private final BooleanSupplier enabled;
    private final SwapPolicy whenOff = new SwapAllMatchingTools();
    private final SwapPolicy whenOn = new ScannerFamilySwapPolicy();

    public ToggleAwareSwapPolicy(BooleanSupplier enabled) {
        this.enabled = Objects.requireNonNull(enabled, "enabled must not be null");
    }

    @Override
    public boolean shouldSwap(HttpRequestToBeSent request) {
        return active().shouldSwap(request);
    }

    private SwapPolicy active() {
        return enabled.getAsBoolean() ? whenOn : whenOff;
    }
}
