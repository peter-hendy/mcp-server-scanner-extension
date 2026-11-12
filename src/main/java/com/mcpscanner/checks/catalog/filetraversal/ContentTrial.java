package com.mcpscanner.checks.catalog.filetraversal;

import java.util.List;

record ContentTrial(List<TraversalAttempt> attempts, List<ConfirmedRead> confirmedReads) {

    List<TraversalAttempt> literalTwins(TraversalPayload payload) {
        return attempts.stream()
                .filter(result -> result.payload().family().equals(payload.family()))
                .filter(result -> payload.tier().literalTiers().contains(result.payload().tier()))
                .toList();
    }
}
