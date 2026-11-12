package com.mcpscanner.checks.catalog.crossclientresponseleak;

import java.util.List;
import java.util.function.Supplier;

final class RetriedRound {

    static final int MAX_ATTEMPTS = 3;

    private RetriedRound() {
    }

    static RoundResult run(Supplier<List<ClientReply>> attempt) {
        RoundResult result = RoundResult.of(attempt.get());
        for (int made = 1; made < MAX_ATTEMPTS && !result.finished(); made++) {
            result = result.followedBy(RoundResult.of(attempt.get()));
        }
        return result;
    }
}
