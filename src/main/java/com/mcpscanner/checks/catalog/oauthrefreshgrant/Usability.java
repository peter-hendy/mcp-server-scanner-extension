package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import com.mcpscanner.checks.shared.auth.oracle.OracleVerdict;

enum Usability {
    USABLE,
    NOT_USABLE,
    UNVERIFIED;

    static Usability of(OracleVerdict verdict) {
        return switch (verdict) {
            case HIT -> USABLE;
            case CANDIDATE_NOT_SUCCESSFUL, CANDIDATE_NOT_EQUIVALENT -> NOT_USABLE;
            case BASELINE_NOT_SUCCESSFUL, NO_REFUSAL_CONTROLS, CONTROL_NOT_REFUSED -> UNVERIFIED;
        };
    }
}
