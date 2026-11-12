package com.mcpscanner.cvetargets.environment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

final class SlotLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;
    private final Runnable onRelease;

    SlotLock(FileChannel channel, FileLock lock, Runnable onRelease) {
        this.channel = channel;
        this.lock = lock;
        this.onRelease = onRelease;
    }

    @Override
    public void close() {
        try (channel) {
            lock.release();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot release CVE target slot", e);
        } finally {
            onRelease.run();
        }
    }
}
