package com.mcpscanner.checks.catalog.crossclientresponseleak;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class RetriedRoundTest {

    private static final List<ClientReply> CROSSED = List.of(reply("a", "echo:b"), ClientReply.unanswered("b"));
    private static final List<ClientReply> CROSSED_LATER = List.of(reply("c", "echo:d"), ClientReply.unanswered("d"));
    private static final List<ClientReply> CLEAN = List.of(reply("a", "echo:a"), reply("b", "echo:b"));
    private static final List<ClientReply> ANSWERED_WITHOUT_MARKERS = List.of(reply("a", "done"), reply("b", "done"));
    private static final List<ClientReply> DEGRADED = List.of(reply("a", "echo:a"), ClientReply.unanswered("b"));
    private static final List<ClientReply> RATE_LIMITED = List.of(new ClientReply("a", 429, "", Optional.empty()),
            reply("b", "echo:b"));

    @Test
    void aCrossingOnALaterAttemptCountsAndStopsTheRound() {
        ScriptedAttempts attempts = new ScriptedAttempts(CLEAN, CROSSED, CLEAN);

        RoundResult result = RetriedRound.run(attempts);

        assertThat(result.crossing()).isPresent();
        assertThat(attempts.made()).isEqualTo(2);
    }

    @Test
    void aRoundWithoutCrossingMakesEveryAttempt() {
        ScriptedAttempts attempts = new ScriptedAttempts(CLEAN, CLEAN, CLEAN, CROSSED);

        RoundResult result = RetriedRound.run(attempts);

        assertThat(result.crossing()).isEmpty();
        assertThat(result.clean()).isTrue();
        assertThat(result.answered()).isTrue();
        assertThat(attempts.made()).isEqualTo(RetriedRound.MAX_ATTEMPTS);
    }

    @Test
    void keepsTheFirstCrossingAttemptsCrossing() {
        RoundResult result = RetriedRound.run(new ScriptedAttempts(CROSSED, CROSSED_LATER));

        assertThat(result.crossing()).hasValueSatisfying(crossing ->
                assertThat(crossing.recipient().marker()).isEqualTo("a"));
    }

    @Test
    void isCleanOnlyWhenEveryAttemptIsClean() {
        RoundResult result = RetriedRound.run(new ScriptedAttempts(CLEAN, CLEAN, ANSWERED_WITHOUT_MARKERS));

        assertThat(result.clean()).isFalse();
        assertThat(result.answered()).isTrue();
    }

    @Test
    void anUnansweredAttemptLeavesARoundWithoutCrossingUnansweredAndUnclean() {
        RoundResult result = RetriedRound.run(new ScriptedAttempts(CLEAN, DEGRADED, CLEAN));

        assertThat(result.crossing()).isEmpty();
        assertThat(result.answered()).isFalse();
        assertThat(result.clean()).isFalse();
    }

    @Test
    void anUnansweredAttemptBeforeACrossingStillCountsAgainstAnswered() {
        RoundResult result = RetriedRound.run(new ScriptedAttempts(DEGRADED, CROSSED));

        assertThat(result.crossing()).isPresent();
        assertThat(result.answered()).isFalse();
    }

    @Test
    void aRateLimitedReplyStopsTheRound() {
        ScriptedAttempts attempts = new ScriptedAttempts(CLEAN, RATE_LIMITED, CLEAN);

        RoundResult result = RetriedRound.run(attempts);

        assertThat(result.rateLimited()).isTrue();
        assertThat(attempts.made()).isEqualTo(2);
    }

    private static ClientReply reply(String marker, String body) {
        return new ClientReply(marker, 200, body, Optional.empty());
    }

    private static final class ScriptedAttempts implements Supplier<List<ClientReply>> {

        private final Deque<List<ClientReply>> script;
        private int made;

        @SafeVarargs
        ScriptedAttempts(List<ClientReply>... attempts) {
            this.script = new ArrayDeque<>(List.of(attempts));
        }

        @Override
        public List<ClientReply> get() {
            made++;
            return script.removeFirst();
        }

        int made() {
            return made;
        }
    }
}
