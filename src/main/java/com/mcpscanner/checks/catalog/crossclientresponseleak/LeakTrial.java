package com.mcpscanner.checks.catalog.crossclientresponseleak;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.catalog.crossclientresponseleak.TrialOutcome.Verdict;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

final class LeakTrial {

    static final int CLIENTS = 6;

    private final ProbeClients clients;
    private final Supplier<String> markers;
    private final ConcurrentRound round;

    LeakTrial(ProbeClients clients, Supplier<String> markers, ConcurrentRound round) {
        this.clients = clients;
        this.markers = markers;
        this.round = round;
    }

    TrialOutcome run(HttpRequestResponse base, EchoableCall call, Http http) {
        List<ProbeClient> opened = new ArrayList<>();
        try {
            return run(base, call, http, opened);
        } finally {
            opened.forEach(ProbeClient::close);
        }
    }

    private TrialOutcome run(HttpRequestResponse base, EchoableCall call, Http http, List<ProbeClient> opened) {
        Opening<ProbeClient> first = clients.open(base, http);
        if (first.opened().isEmpty()) {
            return TrialOutcome.of(first.refusal());
        }
        opened.add(first.opened().get());
        EchoSearch search = EchoSearch.run(first.opened().get(), call, markers);
        if (search.rateLimited()) {
            return TrialOutcome.of(Verdict.RATE_LIMITED);
        }
        return search.echoed()
                .map(echoed -> runWithEchoedArgument(base, call, http, opened, echoed))
                .orElseGet(() -> TrialOutcome.of(Verdict.NO_ECHOED_ARGUMENT));
    }

    private TrialOutcome runWithEchoedArgument(HttpRequestResponse base, EchoableCall call, Http http,
                                               List<ProbeClient> opened, EchoedArgument echoed) {
        String argument = echoed.name();
        Opening<ProbeClient> second = clients.openAnother(base, http);
        if (second.opened().isEmpty()) {
            return TrialOutcome.of(second.refusal(), argument);
        }
        opened.add(second.opened().get());
        ClientReply secondReply = second.opened().get().call(call, argument, markers.get(), RequestIds.SHARED.idFor(1));
        if (secondReply.rateLimited()) {
            return TrialOutcome.of(Verdict.RATE_LIMITED, argument);
        }
        if (!secondReply.echoesOwnMarker() || secondReply.carries(echoed.reply().marker())) {
            return TrialOutcome.of(Verdict.STATE_SHARED_BETWEEN_SEQUENTIAL_CLIENTS, argument);
        }
        Opening<List<ProbeClient>> others = clients.openAll(base, http, CLIENTS - opened.size());
        if (others.opened().isEmpty()) {
            return TrialOutcome.of(others.refusal(), argument);
        }
        opened.addAll(others.opened().get());
        return race(List.copyOf(opened), call, argument);
    }

    private TrialOutcome race(List<ProbeClient> racers, EchoableCall call, String argument) {
        RaceTally tally = RaceTally.empty();
        for (RequestIds ids : RaceTally.SCHEDULE) {
            RoundResult result = RetriedRound.run(() -> round.run(racers, call, argument, ids));
            if (result.rateLimited()) {
                return TrialOutcome.of(Verdict.RATE_LIMITED, argument);
            }
            tally = tally.with(ids, result);
            if (tally.decided()) {
                break;
            }
        }
        Verdict verdict = tally.verdict();
        return new TrialOutcome(verdict, argument,
                verdict == Verdict.CROSSED ? tally.firstCrossing() : Optional.empty());
    }
}
