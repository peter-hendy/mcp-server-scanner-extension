package com.mcpscanner.checks.catalog.crossclientresponseleak;

import com.mcpscanner.checks.catalog.crossclientresponseleak.TrialOutcome.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RaceTallyTest {

    private static final List<ClientReply> CROSSED = List.of(reply("a", "echo:b"), ClientReply.unanswered("b"));
    private static final List<ClientReply> CLEAN = List.of(reply("a", "echo:a"), reply("b", "echo:b"));
    private static final List<ClientReply> ANSWERED_WITHOUT_MARKERS = List.of(reply("a", "done"), reply("b", "done"));
    private static final List<ClientReply> DEGRADED = List.of(reply("a", "echo:a"), ClientReply.unanswered("b"));

    @Test
    void reportsOnlyWhenEverySameIdRoundCrossesAndEveryDistinctIdRoundIsClean() {
        assertThat(tally(CROSSED, CLEAN, CROSSED, CLEAN, CROSSED, CLEAN).verdict()).isEqualTo(Verdict.CROSSED);
    }

    @Test
    void aDistinctIdCrossingMeansSharedState() {
        assertThat(tally(CROSSED, CROSSED).verdict()).isEqualTo(Verdict.STATE_SHARED_UNDER_CONCURRENCY);
        assertThat(tally(CROSSED, CLEAN).decided()).isFalse();
        assertThat(tally(CROSSED, CROSSED).decided()).isTrue();
    }

    @Test
    void aSameIdRoundWithoutCrossingDecidesTheRace() {
        assertThat(tally(CLEAN).decided()).isTrue();
        assertThat(tally(CROSSED, CLEAN, DEGRADED).decided()).isTrue();
        assertThat(tally(CROSSED, CLEAN, CROSSED).decided()).isFalse();
    }

    @Test
    void aSameIdRoundWithoutCrossingIsIntermittent() {
        assertThat(tally(CROSSED, CLEAN, CLEAN).verdict()).isEqualTo(Verdict.INTERMITTENT_CROSSING);
        assertThat(tally(CROSSED, CLEAN, CLEAN, CLEAN, CROSSED, CLEAN).verdict())
                .isEqualTo(Verdict.INTERMITTENT_CROSSING);
    }

    @Test
    void aDegradedDistinctIdRoundConfirmsNothing() {
        assertThat(tally(CROSSED, DEGRADED, CROSSED, CLEAN, CROSSED, CLEAN).verdict())
                .isEqualTo(Verdict.UNANSWERED_RACE);
    }

    @Test
    void aDistinctIdRoundAnsweredWithoutOwnMarkersConfirmsNothing() {
        assertThat(tally(CROSSED, ANSWERED_WITHOUT_MARKERS, CROSSED, CLEAN, CROSSED, CLEAN).verdict())
                .isEqualTo(Verdict.UNANSWERED_RACE);
    }

    @Test
    void noCrossingIsCleanOnlyWhenEveryReplyWasAnswered() {
        assertThat(tally(CLEAN, CLEAN, CLEAN, CLEAN, CLEAN, CLEAN).verdict()).isEqualTo(Verdict.NO_CROSSING);
        assertThat(tally(DEGRADED, CLEAN, CLEAN, CLEAN, CLEAN, CLEAN).verdict()).isEqualTo(Verdict.UNANSWERED_RACE);
    }

    @Test
    void keepsTheFirstSameIdCrossingAsEvidence() {
        RaceTally tally = tally(CROSSED, CLEAN, CROSSED, CLEAN, CROSSED, CLEAN);

        assertThat(tally.firstCrossing()).hasValueSatisfying(crossing ->
                assertThat(crossing.recipient().marker()).isEqualTo("a"));
    }

    @Test
    void aDistinctIdCrossingOnTheLastAttemptMeansSharedState() {
        RoundResult distinct = RoundResult.of(CLEAN).followedBy(RoundResult.of(CLEAN))
                .followedBy(RoundResult.of(CROSSED));
        RaceTally tally = RaceTally.empty().with(RequestIds.SHARED, RoundResult.of(CROSSED))
                .with(RequestIds.DISTINCT, distinct);

        assertThat(tally.decided()).isTrue();
        assertThat(tally.verdict()).isEqualTo(Verdict.STATE_SHARED_UNDER_CONCURRENCY);
    }

    @SafeVarargs
    private static RaceTally tally(List<ClientReply>... rounds) {
        RaceTally tally = RaceTally.empty();
        for (int index = 0; index < rounds.length; index++) {
            tally = tally.with(RaceTally.SCHEDULE.get(index), RoundResult.of(rounds[index]));
        }
        return tally;
    }

    private static ClientReply reply(String marker, String body) {
        return new ClientReply(marker, 200, body, Optional.empty());
    }
}
