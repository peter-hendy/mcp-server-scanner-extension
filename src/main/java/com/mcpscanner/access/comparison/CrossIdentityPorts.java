package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.api.AccessExpectations;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.AccessOutcomeSink;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.ReplayPermission;

public record CrossIdentityPorts(AccessExpectations expectations, AccessOutcomeSink outcomes,
                                 PrincipalListings listings, ObjectReferences references,
                                 ReplayPermission replays) {

    public static CrossIdentityPorts of(AccessLedger ledger, PrincipalListings listings) {
        return of(ledger, listings, ObjectReferences.ALL);
    }

    public static CrossIdentityPorts of(AccessLedger ledger, PrincipalListings listings, ObjectReferences references) {
        return new CrossIdentityPorts(ledger, ledger, listings, references, ledger);
    }
}
