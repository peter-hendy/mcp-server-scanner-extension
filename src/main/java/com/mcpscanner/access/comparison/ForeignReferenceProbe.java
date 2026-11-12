package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.api.crossidentity.Markers;
import com.mcpscanner.checks.api.crossidentity.OperationReply;
import com.mcpscanner.checks.api.crossidentity.OperationSeed;
import com.mcpscanner.checks.api.crossidentity.TripletArms;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Map;
import java.util.Optional;

final class ForeignReferenceProbe {

    static final String FAMILY = "foreign-reference";
    private static final String PROBE = "foreign_reference";

    private enum Witness { NONE, PEER_READ, MARKER }

    private final PrincipalProbe probe;
    private final Map<String, String> references;

    ForeignReferenceProbe(PrincipalProbe probe, Map<String, String> references) {
        this.probe = probe;
        this.references = references;
    }

    void run() {
        OperationSeed seed = probe.kit().seed();
        Optional<ForeignObject> located = ForeignObject.locate(seed, references);
        if (located.isEmpty()) {
            probe.decide(PROBE + ":absent");
            return;
        }
        ForeignObject foreign = located.get();
        HttpRequest request = foreign.substitute(probe.ownerRequest());
        OperationReply direct = probe.reply(request);
        if (!direct.success() || probe.owner().sameResult(direct)) {
            probe.decide(PROBE + ":denied");
            return;
        }
        Witness witness = positiveWitness(foreign, direct);
        if (witness == Witness.NONE) {
            return;
        }
        TripletArms arms = new TripletArms(foreign.absent(probe.ownerRequest(), probe.kit().absent()), request,
                foreign.substitute(probe.anonymous().request()));
        probe.kit().triplet().separate(PROBE, arms)
                .filter(separation -> separation.candidate().sameResult(direct))
                .ifPresent(separation -> probe.report(FindingKind.RESULT_AVAILABLE, FAMILY,
                        witness == Witness.MARKER, observation(foreign, witness), separation.evidence(probe.owner())));
    }

    private String observation(ForeignObject foreign, Witness witness) {
        String identity = probe.principal().name();
        return "The owner's credentials with " + foreign.describe() + " returned "
                + (witness == Witness.PEER_READ ? "exactly " + identity + "'s own result for the same coordinates"
                        : "a result carrying " + identity + "'s private marker")
                + " for " + probe.kit().seed().label() + ", while an absent reference and the anonymous replay did not.";
    }

    private Witness positiveWitness(ForeignObject foreign, OperationReply direct) {
        if (probe.kit().confirms(direct, Markers.of(probe.principal().privateMarker()))) {
            return Witness.MARKER;
        }
        Optional<HttpRequest> peerRead = probe.kit().sessions().fresh(foreign.substitute(probe.asPrincipal()));
        if (peerRead.isPresent() && direct.sameResult(probe.reply(peerRead.get()))) {
            return Witness.PEER_READ;
        }
        probe.decide(PROBE + ":no_positive_witness");
        return Witness.NONE;
    }
}
