package com.mcpscanner.cvetargets.environment;

import com.mcpscanner.cvetargets.manifest.Download;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DownloadCacheTest {

    private static final byte[] ARTIFACT = "package contents".getBytes(StandardCharsets.UTF_8);
    private static final Download PACKAGE = new Download("package.tgz",
            "https://registry.example.test/package.tgz", integrityOf(ARTIFACT));

    @TempDir
    Path root;

    private final List<String> lines = new ArrayList<>();

    @Test
    void reusesACachedFileWhoseDigestMatchesWithoutDownloading() throws IOException {
        Path cache = Files.createDirectories(root.resolve("downloads"));
        Files.write(cache.resolve("package.tgz"), ARTIFACT);
        RecordingSource source = new RecordingSource(ARTIFACT);

        new DownloadCache(cache, source, lines::add).ensure(List.of(PACKAGE));

        assertThat(source.retrieved).isEmpty();
        assertThat(lines).containsExactly("cached package.tgz");
    }

    @Test
    void downloadsAMissingFileIntoANewCacheDirectory() throws IOException {
        Path cache = root.resolve(".cache").resolve("downloads");
        RecordingSource source = new RecordingSource(ARTIFACT);

        new DownloadCache(cache, source, lines::add).ensure(List.of(PACKAGE));

        assertThat(source.retrieved).containsExactly(URI.create(PACKAGE.url()));
        assertThat(Files.readAllBytes(cache.resolve("package.tgz"))).isEqualTo(ARTIFACT);
        assertThat(files(cache)).containsExactly("package.tgz");
        assertThat(lines).containsExactly("downloaded package.tgz from " + PACKAGE.url());
    }

    @Test
    void downloadsAgainACachedFileWhoseDigestNoLongerMatches() throws IOException {
        Path cache = Files.createDirectories(root.resolve("downloads"));
        Files.writeString(cache.resolve("package.tgz"), "truncated");
        RecordingSource source = new RecordingSource(ARTIFACT);

        new DownloadCache(cache, source, lines::add).ensure(List.of(PACKAGE));

        assertThat(source.retrieved).hasSize(1);
        assertThat(Files.readAllBytes(cache.resolve("package.tgz"))).isEqualTo(ARTIFACT);
    }

    @Test
    void refusesADownloadedFileWhoseDigestDiffersAndLeavesNothingBehind() throws IOException {
        Path cache = root.resolve("downloads");
        byte[] tampered = "tampered".getBytes(StandardCharsets.UTF_8);
        DownloadCache downloads = new DownloadCache(cache, new RecordingSource(tampered), lines::add);

        assertThatThrownBy(() -> downloads.ensure(List.of(PACKAGE)))
                .isInstanceOf(EnvironmentException.class)
                .hasMessageContaining("package.tgz")
                .hasMessageContaining(PACKAGE.integrity())
                .hasMessageContaining(integrityOf(tampered));
        assertThat(files(cache)).isEmpty();
    }

    @Test
    void reportsAFailedDownloadAndLeavesNothingBehind() throws IOException {
        Path cache = root.resolve("downloads");
        DownloadSource failing = (url, destination) -> {
            Files.writeString(destination, "partial");
            throw new IOException("HTTP 404");
        };

        assertThatThrownBy(() -> new DownloadCache(cache, failing, lines::add).ensure(List.of(PACKAGE)))
                .isInstanceOf(EnvironmentException.class)
                .hasMessageContaining("package.tgz")
                .hasMessageContaining("HTTP 404");
        assertThat(files(cache)).isEmpty();
    }

    @Test
    void doesNothingForNoDownloads() {
        Path cache = root.resolve("downloads");
        RecordingSource source = new RecordingSource(ARTIFACT);

        new DownloadCache(cache, source, lines::add).ensure(List.of());

        assertThat(source.retrieved).isEmpty();
        assertThat(cache).doesNotExist();
        assertThat(lines).isEmpty();
    }

    private static List<String> files(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.map(path -> path.getFileName().toString()).toList();
        }
    }

    private static String integrityOf(byte[] content) {
        try {
            return "sha512-" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class RecordingSource implements DownloadSource {

        private final byte[] content;
        private final List<URI> retrieved = new ArrayList<>();

        private RecordingSource(byte[] content) {
            this.content = content;
        }

        @Override
        public void retrieve(URI url, Path destination) throws IOException {
            retrieved.add(url);
            Files.write(destination, content);
        }
    }
}
