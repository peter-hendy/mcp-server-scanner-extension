package com.mcpscanner.checks.api;

import java.util.Optional;

@FunctionalInterface
public interface PrincipalListings {

    Optional<Boolean> lists(String principalId, AccessItemKey item);

    static PrincipalListings unknown() {
        return (principalId, item) -> Optional.empty();
    }
}
