package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import java.net.URI;
import java.util.List;
import java.util.Set;

final class CallbackDeliveryOracle {

    private static final Set<Integer> EXPLICIT_REJECTIONS = Set.of(400, 403, 422);

    private final CallbackResponseClassifier classifier = new CallbackResponseClassifier();

    Delivery classify(CallbackPair pair, String state, AuthorizationOutcome control, AuthorizationOutcome probe) {
        if (!control.complete() || !probe.complete()) {
            return Delivery.INCONCLUSIVE;
        }
        if (!classifier.classify(control, pair.registered(), state).delivered()) {
            return Delivery.INCONCLUSIVE;
        }
        Delivery observed = classifier.classify(probe, pair.candidate(), state);
        if (observed.delivered()) {
            return observed;
        }
        return EXPLICIT_REJECTIONS.contains(probe.status()) ? Delivery.REJECTED : Delivery.INCONCLUSIVE;
    }

    Delivery classifyWithNormalisation(CallbackPair pair, List<String> registeredCallbacks, String state,
                                       AuthorizationOutcome control, AuthorizationOutcome probe) {
        URI normalised = pair.candidate().normalize();
        if (normalised.equals(pair.candidate()) || registeredCallbacks.contains(normalised.toString())) {
            return Delivery.INCONCLUSIVE;
        }
        Delivery raw = classify(pair, state, control, probe);
        if (raw.delivered()) {
            return raw;
        }
        Delivery normalisedDelivery = classify(pair.withCandidate(normalised), state, control, probe);
        return normalisedDelivery.delivered() ? normalisedDelivery : raw;
    }
}
