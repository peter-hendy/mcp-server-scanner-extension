package com.mcpscanner.scan;

import com.mcpscanner.config.ExtensionConfigStore;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ScanModeSetting {

    private static final ScanMode DEFAULT = ScanMode.MCP_ONLY;

    private final ExtensionConfigStore configStore;
    private final CopyOnWriteArrayList<Consumer<ScanMode>> listeners = new CopyOnWriteArrayList<>();

    public ScanModeSetting(ExtensionConfigStore configStore) {
        this.configStore = Objects.requireNonNull(configStore);
    }

    public ScanMode current() {
        return ScanMode.named(configStore.scanMode()).orElse(DEFAULT);
    }

    public void select(ScanMode mode) {
        configStore.setScanMode(Objects.requireNonNull(mode).name());
        notifyEveryListener(mode).ifPresent(failure -> {
            throw failure;
        });
    }

    public void addListener(Consumer<ScanMode> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    public void removeListener(Consumer<ScanMode> listener) {
        listeners.remove(listener);
    }

    private Optional<RuntimeException> notifyEveryListener(ScanMode mode) {
        RuntimeException firstFailure = null;
        for (Consumer<ScanMode> listener : listeners) {
            try {
                listener.accept(mode);
            } catch (RuntimeException failure) {
                firstFailure = withSuppressed(firstFailure, failure);
            }
        }
        return Optional.ofNullable(firstFailure);
    }

    private static RuntimeException withSuppressed(RuntimeException firstFailure, RuntimeException failure) {
        if (firstFailure == null) {
            return failure;
        }
        firstFailure.addSuppressed(failure);
        return firstFailure;
    }
}
