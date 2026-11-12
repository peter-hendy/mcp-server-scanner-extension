package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.api.PrincipalListings;

import java.util.List;
import java.util.Optional;

final class VerticalProbe {

    private final PrincipalProbe probe;
    private final PrincipalListings listings;

    VerticalProbe(PrincipalProbe probe, PrincipalListings listings) {
        this.probe = probe;
        this.listings = listings;
    }

    boolean run(IdentityReach reach) {
        if (!reach.ownerResultReached()) {
            return false;
        }
        if (!probe.anonymous().refused()) {
            return probe.rejected("operation_level:anonymous_not_refused");
        }
        boolean hidden = listings.lists(probe.principal().id(), probe.kit().seed().item()).equals(Optional.of(false));
        boolean marker = probe.ownerMarkerConfirms(reach.finalReply());
        probe.decide("operation_level:disallowed_identity_reached");
        probe.report(FindingKind.OPERATION_AVAILABLE, "operation", hidden || marker, observation(reach, hidden, marker),
                List.of(probe.owner().exchange(), probe.anonymous().reply().exchange(),
                        reach.finalReply().exchange()));
        return true;
    }

    private String observation(IdentityReach reach, boolean hidden, boolean marker) {
        String identity = probe.principal().name();
        StringBuilder text = new StringBuilder(identity + "'s credentials" + reach.variant().retryNote()
                + " returned exactly the owner's result for "
                + probe.kit().seed().label() + ", which the access expectation for this item does not allow for "
                + "that identity, while the anonymous request was refused.");
        if (hidden) {
            text.append(" The item is absent from ").append(identity).append("'s own listing.");
        }
        if (marker) {
            text.append(" The owner's private marker, absent from every request sent, appeared in that result.");
        }
        if (!hidden && !marker) {
            text.append(" The item is listed for that identity and no private marker confirmed the result: review "
                    + "whether the expectation is right.");
        }
        return text.toString();
    }
}
