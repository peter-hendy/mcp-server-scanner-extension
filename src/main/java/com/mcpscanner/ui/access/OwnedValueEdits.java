package com.mcpscanner.ui.access;

import com.mcpscanner.identity.EnteredValue;
import com.mcpscanner.identity.MinedValue;
import com.mcpscanner.identity.OwnedValue;
import com.mcpscanner.identity.OwnedValueStore;
import com.mcpscanner.identity.OwnedValues;

import java.util.ArrayList;
import java.util.List;

final class OwnedValueEdits {

    private final OwnedValueStore store;

    OwnedValueEdits(OwnedValueStore store) {
        this.store = store;
    }

    void add(String userId, EnteredValue value) {
        List<EnteredValue> entered = new ArrayList<>(values(userId).entered());
        entered.add(value);
        store.replaceEntered(userId, entered);
    }

    void replace(String userId, EnteredValue current, EnteredValue replacement) {
        store.replaceEntered(userId, values(userId).entered().stream()
                .map(value -> value.equals(current) ? replacement : value).toList());
    }

    void delete(String userId, OwnedValue value) {
        OwnedValues owned = values(userId);
        if (value instanceof MinedValue) {
            store.replaceMined(userId, owned.mined().stream().filter(mined -> !mined.equals(value)).toList());
        } else {
            store.replaceEntered(userId, owned.entered().stream().filter(entered -> !entered.equals(value)).toList());
        }
    }

    private OwnedValues values(String userId) {
        return store.valuesOf(userId);
    }
}
