package com.mcpscanner.checks.catalog.outboundrequest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ConfirmedFields {

    private final Map<FieldSubject, OutboundFinding> strongest = new ConcurrentHashMap<>();

    boolean settled(FieldSubject subject) {
        OutboundFinding confirmed = strongest.get(subject);
        return confirmed != null && confirmed.settles();
    }

    boolean recordIfStronger(FieldSubject subject, OutboundFinding finding) {
        return strongest.merge(subject, finding, ConfirmedFields::stronger) == finding;
    }

    private static OutboundFinding stronger(OutboundFinding existing, OutboundFinding candidate) {
        return candidate.isStrongerThan(existing) ? candidate : existing;
    }
}
