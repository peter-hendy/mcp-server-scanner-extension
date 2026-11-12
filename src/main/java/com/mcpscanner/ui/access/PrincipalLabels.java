package com.mcpscanner.ui.access;

import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;

final class PrincipalLabels {

    private PrincipalLabels() {
    }

    static String column(Principal principal) {
        String role = shortRole(principal.role());
        return principal.name().equalsIgnoreCase(role) ? principal.name() : principal.name() + " (" + role + ")";
    }

    static String described(Principal principal) {
        return principal.name() + " (" + role(principal.role()) + ")";
    }

    static String shortRole(PrincipalRole role) {
        return switch (role) {
            case PRIMARY -> "Primary user";
            case PEER -> "peer";
            case LOWER -> "lower";
            case HIGHER -> "higher";
        };
    }

    private static String role(PrincipalRole role) {
        String label = role.label();
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
