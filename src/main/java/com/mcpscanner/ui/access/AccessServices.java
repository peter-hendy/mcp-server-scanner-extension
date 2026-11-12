package com.mcpscanner.ui.access;

import com.mcpscanner.access.AccessMatrix;
import com.mcpscanner.access.CheckAccessRunner;
import com.mcpscanner.access.OwnerRequests;
import com.mcpscanner.access.WriteReplayConfirmations;

import java.util.Optional;
import java.util.function.IntSupplier;

public record AccessServices(AccessMatrix matrix, CheckAccessRunner runner, OwnerRequests requests,
                             WriteReplayConfirmations confirmations, Runnable refreshListings,
                             IntSupplier identityCount, Optional<MiningServices> mining) {

    public AccessServices(AccessMatrix matrix, CheckAccessRunner runner, OwnerRequests requests,
                          WriteReplayConfirmations confirmations, Runnable refreshListings,
                          IntSupplier identityCount) {
        this(matrix, runner, requests, confirmations, refreshListings, identityCount, Optional.empty());
    }

    public AccessServices withMining(MiningServices services) {
        return new AccessServices(matrix, runner, requests, confirmations, refreshListings, identityCount,
                Optional.of(services));
    }
}
