package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;

/**
 * Observer that does nothing. Injected in off-mode so observation has no effect and behaviour stays
 * byte-identical to the pre-seam handler.
 */
public final class NoopBurpTrafficObserver implements BurpTrafficObserver {

    @Override
    public void observeRequest(HttpRequestToBeSent request) {
    }

    @Override
    public void observeResponse(HttpResponseReceived response) {
    }
}
