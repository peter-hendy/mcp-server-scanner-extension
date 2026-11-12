package com.mcpscanner.ui;

import com.mcpscanner.config.ExtensionConfigStore;

import java.util.function.Supplier;

public final class DontAskAgainMemory {

    private final ExtensionConfigStore configStore;

    public DontAskAgainMemory(ExtensionConfigStore configStore) {
        this.configStore = configStore;
    }

    boolean remembered(String endpoint) {
        return configStore.dontAskAgain(endpoint);
    }

    boolean confirm(String endpoint, Supplier<DestructiveScanConfirmation.Result> ask) {
        if (remembered(endpoint)) {
            return true;
        }
        DestructiveScanConfirmation.Result result = ask.get();
        if (result.proceed() && result.dontAskAgain()) {
            configStore.setDontAskAgain(endpoint, true);
        }
        return result.proceed();
    }
}
