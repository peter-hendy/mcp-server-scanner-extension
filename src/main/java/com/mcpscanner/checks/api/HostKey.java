package com.mcpscanner.checks.api;

import burp.api.montoya.http.HttpService;
import com.mcpscanner.mcp.HttpOrigin;

public record HostKey(HttpOrigin origin) {

    public static HostKey of(HttpService service) {
        return new HostKey(HttpOrigin.of(service));
    }
}
