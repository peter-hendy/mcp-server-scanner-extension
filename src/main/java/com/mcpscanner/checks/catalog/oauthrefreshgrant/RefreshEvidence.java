package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import com.mcpscanner.auth.grant.ClientType;

import java.util.List;
import java.util.Optional;

record RefreshEvidence(ClientType clientType, RefreshExchange control, Optional<RefreshExchange> unissuedControl,
                       List<JudgedRefresh> unauthenticatedAttempts, Optional<ScopeProbe> scopeProbe) {

    RefreshEvidence {
        unauthenticatedAttempts = List.copyOf(unauthenticatedAttempts);
    }

    static RefreshEvidence controlFailed(ClientType clientType, RefreshExchange control) {
        return new RefreshEvidence(clientType, control, Optional.empty(), List.of(), Optional.empty());
    }

    boolean controlsHold() {
        return control.issued() && unissuedControl.map(RefreshExchange::refused).orElse(false);
    }
}
