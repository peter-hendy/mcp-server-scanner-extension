package com.mcpscanner.testutil;

import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.proxy.SseProxyServer;

import java.util.HashMap;
import java.util.Map;

public final class IdentityProbes {

    private IdentityProbes() {}

    public static Map<String, String> headers(SseProxyServer proxy, boolean managedAudit,
                                              Map<String, String> credentials) {
        var headers = new HashMap<>(credentials);
        headers.put(ScannerSentinels.IDENTITY_HEADER, "bob");
        if (managedAudit) headers.putAll(proxy.auditHeaders(headers));
        return headers;
    }
}
