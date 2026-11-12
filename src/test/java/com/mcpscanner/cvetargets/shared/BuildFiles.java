package com.mcpscanner.cvetargets.shared;

import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.TargetRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class BuildFiles {

    private static final Pattern OUTSIDE_PATH = Pattern.compile("\\.\\.[/\\\\]|:\\s*[\"']?[/~]");
    private static final String UNSHARED = "unshared:";

    private BuildFiles() {
    }

    static String identity(LoadedTarget target) {
        Path directory = target.directory();
        List<Path> files = files(directory);
        if (reachesOutside(target.composeFile()) || files.stream().anyMatch(Files::isSymbolicLink)) {
            return UNSHARED + directory.toAbsolutePath();
        }
        return digest(directory, files);
    }

    private static boolean reachesOutside(Path compose) {
        try {
            return OUTSIDE_PATH.matcher(Files.readString(compose)).find();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Cannot read " + compose, unreadable);
        }
    }

    private static String digest(Path directory, List<Path> files) {
        MessageDigest digest = sha256();
        files.stream().filter(Files::isRegularFile)
                .filter(file -> !file.equals(directory.resolve(TargetRepository.MANIFEST_FILE)))
                .forEach(file -> {
                    digest.update(directory.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
                    digest.update(bytes(file));
                });
        return HexFormat.of().formatHex(digest.digest());
    }

    private static List<Path> files(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.sorted().toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Cannot list " + directory, unreadable);
        }
    }

    private static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Cannot read " + file, unreadable);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }
}
