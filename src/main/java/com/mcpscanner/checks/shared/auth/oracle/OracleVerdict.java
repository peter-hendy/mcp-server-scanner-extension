package com.mcpscanner.checks.shared.auth.oracle;

public enum OracleVerdict {
    HIT,
    BASELINE_NOT_SUCCESSFUL,
    NO_REFUSAL_CONTROLS,
    CONTROL_NOT_REFUSED,
    CANDIDATE_NOT_SUCCESSFUL,
    CANDIDATE_NOT_EQUIVALENT;

    public boolean isHit() {
        return this == HIT;
    }
}
