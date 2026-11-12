package com.mcpscanner.checks.catalog.oauthrefreshgrant;

record ScopeProbe(String extraScope, OriginalScope original, JudgedRefresh refresh, boolean restored) {

    boolean expanded() {
        return refresh.exchange().grantedScope().map(scopes -> scopes.contains(extraScope)).orElse(false);
    }

    static boolean provenWithin(RefreshExchange exchange, OriginalScope original) {
        return exchange.grantedScope().map(granted -> original.scopes().containsAll(granted)).orElse(false);
    }
}
