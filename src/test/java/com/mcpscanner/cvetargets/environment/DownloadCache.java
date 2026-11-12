package com.mcpscanner.cvetargets.environment;

import com.mcpscanner.cvetargets.manifest.Download;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Consumer;

public final class DownloadCache {

    private final Path directory;
    private final DownloadSource source;
    private final Consumer<String> log;

    public DownloadCache(Path directory, DownloadSource source, Consumer<String> log) {
        this.directory = directory;
        this.source = source;
        this.log = log;
    }

    public void ensure(List<Download> downloads) {
        downloads.forEach(this::ensure);
    }

    private void ensure(Download download) {
        Path cached = directory.resolve(download.file());
        if (Files.isRegularFile(cached) && Sha512Integrity.of(cached).equals(download.integrity())) {
            log.accept("cached " + download.file());
            return;
        }
        storeFresh(cached, download);
        log.accept("downloaded " + download.file() + " from " + download.url());
    }

    private void storeFresh(Path cached, Download download) {
        Path partial = createPartial(download);
        try {
            retrieve(download, partial);
            requireIntegrity(download, partial);
            Files.move(partial, cached, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store " + download.file() + " in " + directory, e);
        } finally {
            deleteQuietly(partial);
        }
    }

    private Path createPartial(Download download) {
        try {
            Files.createDirectories(directory);
            return Files.createTempFile(directory, download.file() + ".", ".part");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the download cache " + directory, e);
        }
    }

    private void retrieve(Download download, Path partial) {
        try {
            source.retrieve(URI.create(download.url()), partial);
        } catch (IOException e) {
            throw new EnvironmentException("cannot download " + download.file() + " from " + download.url() + ": "
                    + e.getMessage());
        }
    }

    private static void requireIntegrity(Download download, Path partial) {
        String actual = Sha512Integrity.of(partial);
        if (!actual.equals(download.integrity())) {
            throw new EnvironmentException("download " + download.file() + " failed its integrity check: expected "
                    + download.integrity() + " but got " + actual);
        }
    }

    private static void deleteQuietly(Path partial) {
        try {
            Files.deleteIfExists(partial);
        } catch (IOException ignored) {
            partial.toFile().deleteOnExit();
        }
    }
}
