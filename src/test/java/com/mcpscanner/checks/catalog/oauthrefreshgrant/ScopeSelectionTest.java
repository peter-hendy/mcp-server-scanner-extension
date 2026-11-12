package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeSelectionTest {

    private final ScopeSelection selection = new ScopeSelection();

    @Test
    void picksTheFirstAdvertisedPrivilegeScopeNotAlreadyGranted() {
        assertThat(selection.extraScope(Set.of("mcp:tools", "mcp:resources", "mcp:admin"), Set.of("mcp:tools")))
                .contains("mcp:admin");
    }

    @Test
    void neverPicksIdentityOrOfflineScopes() {
        assertThat(selection.extraScope(Set.of("openid", "profile", "email", "offline_access", "address", "phone"),
                Set.of())).isEmpty();
    }

    @Test
    void findsNothingWhenEverythingIsGranted() {
        assertThat(selection.extraScope(Set.of("a"), Set.of("a"))).isEmpty();
    }
}
