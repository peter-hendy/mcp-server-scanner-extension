package com.mcpscanner.identity;

public enum IdentityRole {
    PEER("Same access (peer)"),
    LOWER("Less access"),
    HIGHER("More access");

    private final String label;

    IdentityRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
