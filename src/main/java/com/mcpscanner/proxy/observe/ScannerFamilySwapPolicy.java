package com.mcpscanner.proxy.observe;

import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.HttpRequestToBeSent;

/**
 * Swaps only requests issued by the scanner family — Repeater, Scanner, and Intruder. Live Proxy
 * traffic and the loopback proxy's own upstream hop (Extensions) flow through unswapped, which
 * preserves the live stream and prevents the proxy from recursing into itself.
 */
public final class ScannerFamilySwapPolicy implements SwapPolicy {

    @Override
    public boolean shouldSwap(HttpRequestToBeSent request) {
        return request.toolSource()
                .isFromTool(ToolType.REPEATER, ToolType.SCANNER, ToolType.INTRUDER);
    }
}
