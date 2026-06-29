package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;

/**
 * Observes matching MCP traffic that the handler did not swap to the loopback proxy. Lets the
 * handler tap live (un-swapped) requests and responses without itself knowing what to do with
 * them — the observe destination varies by injected implementation.
 */
public interface BurpTrafficObserver {

    void observeRequest(HttpRequestToBeSent request);

    void observeResponse(HttpResponseReceived response);
}
