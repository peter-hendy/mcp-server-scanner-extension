package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.api.Principal;

final class FindingSummary {

    private FindingSummary() {
    }

    static String of(Finding finding, Principal owner) {
        String identity = finding.principal().name();
        String ownerName = owner.name();
        return switch (finding.kind()) {
            case OPERATION_AVAILABLE -> "Operation-level: " + identity + " reached an operation the expectation "
                    + "does not allow it, with " + ownerName + "'s result" + strength(finding, owner);
            case RESULT_AVAILABLE -> resultSummary(finding, owner, identity, ownerName);
        };
    }

    private static String resultSummary(Finding finding, Principal owner, String identity, String ownerName) {
        if (finding.family().equals(IdentityFindings.MARKER_REPLAY)) {
            return "Object-level: " + identity + " received " + ownerName + "'s private marker";
        }
        if (finding.family().equals(ForeignReferenceProbe.FAMILY)) {
            return "Object-level: " + ownerName + "'s credentials reached " + identity + "'s object"
                    + strength(finding, finding.principal());
        }
        return "Object-level: " + identity + " received " + ownerName + "'s result for the same arguments"
                + strength(finding, owner);
    }

    private static String strength(Finding finding, Principal markerHolder) {
        if (finding.firm()) {
            return " (Firm — " + markerHolder.name() + "'s private marker appeared)";
        }
        return markerHolder.privateMarker().filter(marker -> !marker.isBlank()).isPresent()
                ? " (Tentative — " + markerHolder.name() + "'s private marker did not appear)"
                : " (Tentative — no private marker configured for " + markerHolder.name() + ")";
    }
}
