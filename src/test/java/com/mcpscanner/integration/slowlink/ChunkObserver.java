package com.mcpscanner.integration.slowlink;

@FunctionalInterface
interface ChunkObserver {

    ChunkObserver NONE = (chunk, length) -> { };

    void observe(byte[] chunk, int length);
}
