package com.mcpscanner.checks.api;

public enum AccessExpectation {
    ANY_SIGNED_IN("Any signed-in user"),
    NOT_LOWER("Not lower privilege"),
    OWNER_ONLY("Owner only"),
    HIGHER_ONLY("Higher privilege only"),
    SHARED_BY_DESIGN("Shared by design");

    private final String label;

    AccessExpectation(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean forbids(PrincipalRole role) {
        return switch (this) {
            case ANY_SIGNED_IN, SHARED_BY_DESIGN -> false;
            case NOT_LOWER -> role == PrincipalRole.LOWER;
            case OWNER_ONLY -> true;
            case HIGHER_ONLY -> role != PrincipalRole.HIGHER;
        };
    }
}
