package com.mcpscanner.checks.api.crossidentity;

import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;

import java.util.Map;
import java.util.Optional;

public final class TestPrincipals {

    private TestPrincipals() {
    }

    public static Principal owner(Optional<String> marker) {
        return new Principal(Principal.PRIMARY_ID, Principal.PRIMARY_NAME, PrincipalRole.PRIMARY,
                () -> Map.of("Authorization", "Bearer A"), marker, Map.of());
    }

    public static Principal peer(String token) {
        return identity(token, PrincipalRole.PEER, Map.of("Authorization", "Bearer " + token), Optional.empty(),
                Map.of());
    }

    public static Principal identity(String token, PrincipalRole role, Map<String, String> headers,
                                     Optional<String> marker, Map<String, String> owned) {
        return new Principal(token.toLowerCase(), "identity-" + token, role, () -> headers, marker, owned);
    }
}
