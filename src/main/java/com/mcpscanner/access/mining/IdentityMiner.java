package com.mcpscanner.access.mining;

import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.shared.probe.SendBudget;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class IdentityMiner {

    private static final int SENDS_PER_CALL = 2;

    private final MiningSender sender;
    private final MiningSettings settings;
    private final CallPlanner planner;
    private final ReplySightings replySightings;
    private final MiningControl control;

    IdentityMiner(MiningSender sender, MiningSettings settings, CallPlanner planner, ReplySightings replySightings,
                  MiningControl control) {
        this.sender = sender;
        this.settings = settings;
        this.planner = planner;
        this.replySightings = replySightings;
        this.control = control;
    }

    IdentityRun mine(Principal identity) {
        return new Session(identity).run();
    }

    private final class Session {
        private final Principal identity;
        private final IdentitySightings sightings = new IdentitySightings(settings.valuesPerArgument());
        private final SendBudget budget = new SendBudget(settings.requestsPerIdentity());
        private final StableExchange exchange;
        private final Set<String> sentCalls = new HashSet<>();
        private boolean limitReached;

        Session(Principal identity) {
            this.identity = identity;
            this.exchange = new StableExchange(sender, identity, budget);
        }

        IdentityRun run() {
            MiningStop stop = MiningStop.COMPLETED;
            for (int pass = 1; pass <= settings.passes() && stop == MiningStop.COMPLETED && !limitReached; pass++) {
                PassOutcome outcome = runPass(pass, pass == 1 ? planner.firstPass() : planner.fedPass(sightings));
                stop = outcome.stop();
                if (outcome.found() == 0) {
                    break;
                }
            }
            return new IdentityRun(identity.id(), sightings, budget.sent(), limitReached, stop);
        }

        private PassOutcome runPass(int pass, List<MiningCall> planned) {
            int found = 0;
            Deque<MiningCall> queue = new ArrayDeque<>(approved(planned));
            while (!queue.isEmpty()) {
                MiningCall call = queue.poll();
                if (!sentCalls.add(key(call))) {
                    continue;
                }
                if (control.cancelled().getAsBoolean()) {
                    return new PassOutcome(found, MiningStop.CANCELLED);
                }
                if (!budget.affords(SENDS_PER_CALL)) {
                    limitReached = true;
                    return new PassOutcome(found, MiningStop.COMPLETED);
                }
                Optional<StableExchange.Replies> replies = exchange.send(call);
                if (replies.isEmpty()) {
                    return new PassOutcome(found, MiningStop.RATE_LIMITED);
                }
                found += sightings.add(replySightings.stable(call, replies.get().first(), replies.get().second()));
                nextPage(call, replies.get().first()).ifPresent(queue::addFirst);
                control.progress().progressed(new MiningStep(identity.id(), pass, budget.sent()));
            }
            return new PassOutcome(found, MiningStop.COMPLETED);
        }

        private Optional<MiningCall> nextPage(MiningCall call, MiningReply reply) {
            return call instanceof MiningCall.ResourceListing listing && reply.usable()
                    ? listing.next(reply.result(), settings.listPages()).map(MiningCall.class::cast)
                    : Optional.empty();
        }

        private List<MiningCall> approved(List<MiningCall> planned) {
            List<MiningCall> unsent = planned.stream().filter(call -> !sentCalls.contains(key(call)))
                    .filter(new DistinctCalls()::firstSeen).toList();
            return new WriteCallScreen(control.writes()).approved(identity.name(), unsent);
        }
    }

    private static String key(MiningCall call) {
        return call.method() + call.params();
    }

    private static final class DistinctCalls {
        private final Set<String> seen = new HashSet<>();

        boolean firstSeen(MiningCall call) {
            return seen.add(key(call));
        }
    }

    private record PassOutcome(int found, MiningStop stop) {
    }
}
