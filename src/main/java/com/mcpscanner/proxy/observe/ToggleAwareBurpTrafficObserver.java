package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * {@link BurpTrafficObserver} that switches behaviour on the live MCP-proxy toggle.
 *
 * <p>When the toggle is OFF it is a no-op — the delegate is never touched, so live traffic is not
 * tapped and behaviour stays byte-identical to the pre-feature handler. When the toggle is ON it
 * forwards to the supplied delegate (in production a {@link BurpObserverAdapter} feeding the
 * {@link McpExchangeLog}).
 *
 * <p>The toggle is read live on every call so a runtime flip takes effect without reconstructing the
 * handler.
 */
public final class ToggleAwareBurpTrafficObserver implements BurpTrafficObserver {

    private final BooleanSupplier enabled;
    private final BurpTrafficObserver delegate;

    public ToggleAwareBurpTrafficObserver(BooleanSupplier enabled, BurpTrafficObserver delegate) {
        this.enabled = Objects.requireNonNull(enabled, "enabled must not be null");
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public void observeRequest(HttpRequestToBeSent request) {
        if (enabled.getAsBoolean()) {
            delegate.observeRequest(request);
        }
    }

    @Override
    public void observeResponse(HttpResponseReceived response) {
        if (enabled.getAsBoolean()) {
            delegate.observeResponse(response);
        }
    }
}
