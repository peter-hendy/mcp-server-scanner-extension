package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.mcp.ScannerSentinels;

public final class OwnerSelection {

    private OwnerSelection() {
    }

    public static Principal of(HttpRequest base, PrincipalRoster roster) {
        String routedAs = base == null ? null : base.headerValue(ScannerSentinels.IDENTITY_HEADER);
        return roster.compared().stream()
                .filter(principal -> principal.id().equals(routedAs))
                .findFirst()
                .orElse(roster.primary());
    }
}
