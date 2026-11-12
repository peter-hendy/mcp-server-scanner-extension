package com.mcpscanner.testutil;

import com.mcpscanner.config.ExtensionConfigStore;

public final class ScratchConfig {

    private ScratchConfig() {
    }

    public static ExtensionConfigStore store() {
        return InMemoryPersistedObject.configStore();
    }
}
