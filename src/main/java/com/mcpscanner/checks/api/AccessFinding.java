package com.mcpscanner.checks.api;

import java.util.Objects;

public record AccessFinding(Kind kind, String summary) {

    public enum Kind {
        OPERATION_LEVEL,
        OBJECT_LEVEL,
        POSSIBLE_OBJECT_LEVEL
    }

    public AccessFinding {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
    }

    public boolean reported() {
        return kind != Kind.POSSIBLE_OBJECT_LEVEL;
    }

    public boolean breaksExpectation() {
        return kind == Kind.OPERATION_LEVEL;
    }
}
