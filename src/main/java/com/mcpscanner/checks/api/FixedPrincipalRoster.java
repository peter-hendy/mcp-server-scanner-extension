package com.mcpscanner.checks.api;

import java.util.List;
import java.util.Objects;

record FixedPrincipalRoster(Principal primary, List<Principal> compared) implements PrincipalRoster {

    FixedPrincipalRoster {
        Objects.requireNonNull(primary, "primary must not be null");
        compared = List.copyOf(compared);
    }
}
