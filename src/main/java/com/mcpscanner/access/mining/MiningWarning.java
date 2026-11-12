package com.mcpscanner.access.mining;

public record MiningWarning(Kind kind, String identityId, String detail) {

    public enum Kind {
        EMPTY,
        REQUEST_LIMIT,
        SAME_TENANT
    }
}
