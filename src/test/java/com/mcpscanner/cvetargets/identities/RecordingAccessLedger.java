package com.mcpscanner.cvetargets.identities;

import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.AccessOutcome;
import com.mcpscanner.cvetargets.manifest.AccessItem;
import com.mcpscanner.cvetargets.manifest.IdentitySetup;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public final class RecordingAccessLedger implements AccessLedger {

    private final Map<AccessItemKey, AccessExpectation> expectations;
    private final List<AccessRecord> records = new CopyOnWriteArrayList<>();

    private RecordingAccessLedger(Map<AccessItemKey, AccessExpectation> expectations) {
        this.expectations = Map.copyOf(expectations);
    }

    public static RecordingAccessLedger declaredBy(IdentitySetup identities) {
        return new RecordingAccessLedger(identities.items().stream()
                .filter(item -> item.expectation() != null)
                .collect(Collectors.toMap(AccessItem::key, AccessItem::expectation, (first, second) -> first)));
    }

    @Override
    public AccessExpectation expectationFor(AccessItemKey item) {
        return expectations.getOrDefault(item, AccessExpectation.ANY_SIGNED_IN);
    }

    @Override
    public boolean permitsReplay(AccessItemKey item) {
        return true;
    }

    @Override
    public void record(AccessItemKey item, String principalId, AccessOutcome outcome) {
        records.add(new AccessRecord(item.kind(), item.name(), principalId, outcome));
    }

    @Override
    public void recordAnonymous(AccessItemKey item, AccessOutcome outcome) {
        record(item, IdentitySetup.ANONYMOUS_CALLER, outcome);
    }

    public List<AccessRecord> records() {
        return List.copyOf(records);
    }
}
