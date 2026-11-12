package com.mcpscanner.access.mining;

import com.mcpscanner.identity.OwnedValueStore;
import com.mcpscanner.logging.McpEventLog;

import java.util.Map;

final class MinedValueWriter {

    private final OwnedValueStore store;
    private final McpEventLog log;

    MinedValueWriter(OwnedValueStore store, McpEventLog log) {
        this.store = store;
        this.log = log;
    }

    void write(MiningResult result, Map<String, String> identityIdsByPrincipal) {
        result.identities().forEach(mining -> {
            String identityId = identityIdsByPrincipal.get(mining.identityId());
            if (identityId != null) {
                save(identityId, mining);
            }
        });
    }

    private void save(String identityId, IdentityMining mining) {
        try {
            store.replaceMined(identityId, mining.values());
        } catch (RuntimeException removed) {
            log.warn("Collecting IDs: could not save values for a user removed during the run: " + removed.getMessage());
        }
    }
}
