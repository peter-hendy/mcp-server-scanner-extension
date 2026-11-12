package com.mcpscanner.checks.api;

public enum PrincipalRole {
    LOWER(0, "lower privilege"),
    PRIMARY(1, "Primary user"),
    PEER(1, "peer"),
    HIGHER(2, "higher privilege");

    private final int rank;
    private final String label;

    PrincipalRole(int rank, String label) {
        this.rank = rank;
        this.label = label;
    }

    public int rank() {
        return rank;
    }

    public String label() {
        return label;
    }
}
