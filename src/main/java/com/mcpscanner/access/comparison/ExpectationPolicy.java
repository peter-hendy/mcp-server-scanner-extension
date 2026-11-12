package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.PrincipalRole;

final class ExpectationPolicy {

    private ExpectationPolicy() {
    }

    static boolean comparesObjects(AccessExpectation expectation, PrincipalRole role) {
        return expectation != AccessExpectation.SHARED_BY_DESIGN
                && (role == PrincipalRole.PEER || role == PrincipalRole.LOWER);
    }
}
