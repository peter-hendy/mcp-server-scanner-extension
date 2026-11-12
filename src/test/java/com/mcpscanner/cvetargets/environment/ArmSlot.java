package com.mcpscanner.cvetargets.environment;

import java.util.List;

public final class ArmSlot implements AutoCloseable {

    private final List<SlotLock> locks;

    ArmSlot(List<SlotLock> locks) {
        this.locks = List.copyOf(locks);
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        for (SlotLock lock : locks) {
            try {
                lock.close();
            } catch (RuntimeException releaseFailure) {
                failure = combined(failure, releaseFailure);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException combined(RuntimeException first, RuntimeException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }
}
