package com.mcpscanner.access.mining;

import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.shared.probe.SendBudget;

import java.util.Optional;

final class StableExchange {

    private final MiningSender sender;
    private final Principal identity;
    private final SendBudget budget;

    StableExchange(MiningSender sender, Principal identity, SendBudget budget) {
        this.sender = sender;
        this.identity = identity;
        this.budget = budget;
    }

    Optional<Replies> send(MiningCall call) {
        Optional<MiningReply> first = sent(call);
        Optional<MiningReply> second = first.flatMap(reply -> sent(call));
        if (second.isEmpty()) {
            return Optional.empty();
        }
        return first.get().usable() == second.get().usable() ? Optional.of(new Replies(first.get(), second.get()))
                : retried(call, first.get().usable() ? first.get() : second.get());
    }

    private Optional<Replies> retried(MiningCall call, MiningReply succeeded) {
        if (!budget.affords(1)) {
            return Optional.of(new Replies(succeeded, MiningReply.unanswered()));
        }
        return sent(call).map(retry -> new Replies(succeeded, retry));
    }

    private Optional<MiningReply> sent(MiningCall call) {
        MiningReply reply = sender.send(identity, call);
        budget.record(reply.rateLimited());
        return reply.rateLimited() ? Optional.empty() : Optional.of(reply);
    }

    record Replies(MiningReply first, MiningReply second) {
    }
}
