package com.mcpscanner.checks.shared.oauth;

public record ClientCleanup(int registered, int deleted) {

    public String summary() {
        return "deleted " + deleted + " of " + registered + " scanner-registered OAuth clients through RFC 7592";
    }
}
