package com.mcpscanner.identity;

import java.util.List;

record PersistedOwnedValue(String argument, String value, Provenance provenance, MinedSource source,
                           ValueScope scope) {

    static List<PersistedOwnedValue> fromValues(OwnedValues values) {
        return values.all().stream().map(PersistedOwnedValue::from).toList();
    }

    static OwnedValues toValues(List<PersistedOwnedValue> persisted) {
        return persisted == null ? OwnedValues.NONE
                : OwnedValues.of(persisted.stream().map(PersistedOwnedValue::toValue).toList());
    }

    private static PersistedOwnedValue from(OwnedValue owned) {
        if (owned instanceof MinedValue mined) {
            return new PersistedOwnedValue(mined.argument(), mined.value(), Provenance.MINED, mined.source(),
                    mined.scope());
        }
        return new PersistedOwnedValue(owned.argument(), owned.value(), Provenance.ENTERED, null, null);
    }

    private OwnedValue toValue() {
        return provenance == Provenance.MINED ? new MinedValue(argument, value, source, scope)
                : new EnteredValue(argument, value);
    }
}
