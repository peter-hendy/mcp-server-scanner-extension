package com.mcpscanner.checks.api;

public interface ReplayPermission {

    boolean permitsReplay(AccessItemKey item);

    default boolean replaysWrite(AccessItemKey item) {
        return item.kind() == AccessItemKey.Kind.TOOL && permitsReplay(item);
    }
}
