package com.mcpscanner.ui.access;

import com.mcpscanner.access.mining.AccessMining;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.OwnedValueStore;

import java.util.List;
import java.util.function.Supplier;

public record MiningServices(AccessMining mining, OwnedValueStore ownedValues, Supplier<List<Identity>> users,
                             MiningDialogs dialogs) {
}
