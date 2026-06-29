package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.handler.HttpRequestToBeSent;

/**
 * Decides whether a matching outbound MCP request should be swapped to the local loopback proxy.
 * Lets the handler vary the swap decision (e.g. swap every tool today, or only the scanner family)
 * without changing how the swap itself is performed.
 */
public interface SwapPolicy {

    boolean shouldSwap(HttpRequestToBeSent request);
}
