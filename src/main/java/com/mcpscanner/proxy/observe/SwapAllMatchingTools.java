package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.handler.HttpRequestToBeSent;

/**
 * Swaps every matching request regardless of which tool issued it, reproducing the handler's
 * original behaviour exactly. Deliberately reads nothing tool-related from the request so it stays
 * byte-for-byte compatible with the pre-policy handler.
 */
public final class SwapAllMatchingTools implements SwapPolicy {

    @Override
    public boolean shouldSwap(HttpRequestToBeSent request) {
        return true;
    }
}
