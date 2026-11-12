package com.mcpscanner.checks.api.crossidentity;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Optional;

public final class ProbeTriplet {

    private final ProbeTransport transport;
    private final DecisionLog log;

    public ProbeTriplet(ProbeTransport transport, DecisionLog log) {
        this.transport = transport;
        this.log = log;
    }

    public Optional<Separation> separate(String probe, TripletArms arms) {
        Optional<OperationReply> baseline = stableArm(arms.baseline());
        Optional<OperationReply> candidate = stableArm(arms.candidate());
        Optional<OperationReply> control = stableArm(arms.control());
        if (transport.stopped()) {
            return Optional.empty();
        }
        if (baseline.isEmpty() || candidate.isEmpty() || control.isEmpty()) {
            log.record(probe + ":triplet_not_separated:unstable_arm");
            return Optional.empty();
        }
        String signature = candidate.get().signature();
        if (signature.equals(baseline.get().signature()) || signature.equals(control.get().signature())) {
            log.record(probe + ":triplet_not_separated");
            return Optional.empty();
        }
        return Optional.of(new Separation(baseline.get(), candidate.get(), control.get()));
    }

    private Optional<OperationReply> stableArm(HttpRequest request) {
        OperationReply first = OperationReply.of(transport.sample(request));
        OperationReply second = OperationReply.of(transport.sample(request));
        boolean stable = first.answered() && second.answered() && first.signature().equals(second.signature());
        return stable ? Optional.of(first) : Optional.empty();
    }
}
