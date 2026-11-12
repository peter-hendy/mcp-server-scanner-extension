package com.mcpscanner.cvetargets.burp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

final class WorkDirectory {

    private WorkDirectory() {
    }

    static Path createUnder(Path root) {
        try {
            Files.createDirectories(root);
            return Files.createTempDirectory(root, "burp-");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static void delete(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
