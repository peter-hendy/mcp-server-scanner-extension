package com.mcpscanner.checks.catalog.crossclientresponseleak;

import java.util.List;
import java.util.Optional;

record RoundResult(Optional<Crossing> crossing, boolean answered, boolean clean, boolean rateLimited) {

    static RoundResult of(List<ClientReply> replies) {
        return new RoundResult(Crossing.in(replies),
                replies.stream().allMatch(ClientReply::answered),
                replies.stream().allMatch(reply -> reply.answered() && reply.echoesOwnMarker()),
                replies.stream().anyMatch(ClientReply::rateLimited));
    }

    RoundResult followedBy(RoundResult attempt) {
        return new RoundResult(crossing.or(attempt::crossing), answered && attempt.answered,
                clean && attempt.clean, rateLimited || attempt.rateLimited);
    }

    boolean finished() {
        return crossing.isPresent() || rateLimited;
    }
}
