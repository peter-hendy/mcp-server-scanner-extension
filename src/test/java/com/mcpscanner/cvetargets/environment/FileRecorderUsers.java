package com.mcpscanner.cvetargets.environment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

public final class FileRecorderUsers implements RecorderUsers {

    private static final String SETUP_LOCK = "setup.lock";
    private static final String USERS_LOCK = "users.lock";

    private final Path directory;
    private FileLock membership;

    public FileRecorderUsers(Path directory) {
        this.directory = directory;
    }

    @Override
    public synchronized void setUp(Consumer<RecorderOccupancy> setup) {
        try (FileChannel channel = open(SETUP_LOCK); FileLock ignored = channel.lock()) {
            setup.accept(occupancy());
            join();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot coordinate the interaction recorder under " + directory, e);
        }
    }

    private RecorderOccupancy occupancy() throws IOException {
        if (membership != null) {
            return RecorderOccupancy.IN_USE;
        }
        try (FileChannel channel = open(USERS_LOCK); FileLock alone = channel.tryLock()) {
            return alone == null ? RecorderOccupancy.IN_USE : RecorderOccupancy.UNUSED;
        }
    }

    private void join() throws IOException {
        if (membership == null) {
            membership = open(USERS_LOCK).lock(0, Long.MAX_VALUE, true);
        }
    }

    private FileChannel open(String name) throws IOException {
        Files.createDirectories(directory);
        return FileChannel.open(directory.resolve(name), StandardOpenOption.CREATE, StandardOpenOption.READ,
                StandardOpenOption.WRITE);
    }
}
