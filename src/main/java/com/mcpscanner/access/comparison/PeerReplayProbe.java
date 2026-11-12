package com.mcpscanner.access.comparison;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.Leaf;
import com.mcpscanner.checks.api.crossidentity.Separation;
import com.mcpscanner.checks.api.crossidentity.TripletArms;

import java.util.Map;
import java.util.Optional;

final class PeerReplayProbe {

    static final String FAMILY = "peer-replay";
    private static final String PROBE = "peer_replay";

    private final PrincipalProbe probe;
    private final SelectorControl selectors;
    private final Map<String, String> ownerReferences;

    PeerReplayProbe(PrincipalProbe probe, Map<String, String> ownerReferences) {
        this.probe = probe;
        this.ownerReferences = ownerReferences;
        this.selectors = new SelectorControl(probe.kit());
    }

    void run(HttpRequest peerRequest) {
        Optional<Leaf> selector = selectors.find(probe.ownerRequest(), ownerReferences.values());
        if (selector.isEmpty()) {
            probe.decide(PROBE + ":no_object_selector");
            return;
        }
        TripletArms arms = new TripletArms(probe.anonymous().request(), peerRequest,
                selectors.control(peerRequest, selector.get()));
        probe.kit().triplet().separate(PROBE, arms)
                .filter(separation -> probe.ownerResultReturned(PROBE, separation.candidate()))
                .ifPresent(separation -> report(selector.get(), separation));
    }

    private void report(Leaf selector, Separation separation) {
        String identity = probe.principal().name();
        probe.report(FindingKind.RESULT_AVAILABLE, FAMILY, probe.ownerMarkerConfirms(separation.candidate()),
                identity + "'s credentials returned exactly the owner's result for " + probe.kit().seed().label()
                        + ", which carries the owner's value in " + selector.label() + ", while the anonymous "
                        + "replay and " + identity + "'s request with " + selector.label()
                        + " set to an invented value of the same shape did not.",
                separation.evidence(probe.owner()));
    }
}
