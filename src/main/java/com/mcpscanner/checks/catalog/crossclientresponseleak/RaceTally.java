package com.mcpscanner.checks.catalog.crossclientresponseleak;

import com.mcpscanner.checks.catalog.crossclientresponseleak.TrialOutcome.Verdict;

import java.util.List;
import java.util.Optional;

record RaceTally(int sameIdRounds, int sameIdCrossings, int distinctIdCrossings, int degradedDistinctIdRounds,
                 boolean everyRoundClean, Optional<Crossing> firstCrossing) {

    static final List<RequestIds> SCHEDULE = List.of(RequestIds.SHARED, RequestIds.DISTINCT,
            RequestIds.SHARED, RequestIds.DISTINCT, RequestIds.SHARED, RequestIds.DISTINCT);

    static RaceTally empty() {
        return new RaceTally(0, 0, 0, 0, true, Optional.empty());
    }

    RaceTally with(RequestIds ids, RoundResult round) {
        return ids == RequestIds.SHARED ? withSameIdRound(round) : withDistinctIdRound(round);
    }

    boolean decided() {
        return distinctIdCrossings > 0 || sameIdCrossings < sameIdRounds;
    }

    Verdict verdict() {
        if (distinctIdCrossings > 0) {
            return Verdict.STATE_SHARED_UNDER_CONCURRENCY;
        }
        if (sameIdCrossings == 0) {
            return everyRoundClean ? Verdict.NO_CROSSING : Verdict.UNANSWERED_RACE;
        }
        if (degradedDistinctIdRounds > 0) {
            return Verdict.UNANSWERED_RACE;
        }
        return sameIdCrossings == sameIdRounds ? Verdict.CROSSED : Verdict.INTERMITTENT_CROSSING;
    }

    private RaceTally withSameIdRound(RoundResult round) {
        return new RaceTally(sameIdRounds + 1, sameIdCrossings + crossings(round),
                distinctIdCrossings, degradedDistinctIdRounds, everyRoundClean && round.answered(),
                firstCrossing.or(round::crossing));
    }

    private RaceTally withDistinctIdRound(RoundResult round) {
        boolean degraded = round.crossing().isEmpty() && !round.clean();
        return new RaceTally(sameIdRounds, sameIdCrossings, distinctIdCrossings + crossings(round),
                degradedDistinctIdRounds + (degraded ? 1 : 0), everyRoundClean && round.clean(), firstCrossing);
    }

    private static int crossings(RoundResult round) {
        return round.crossing().isPresent() ? 1 : 0;
    }
}
