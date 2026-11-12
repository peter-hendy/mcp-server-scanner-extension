package com.mcpscanner.cvetargets.environment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

final class Sha512Integrity {

    private static final String PREFIX = "sha512-";

    private Sha512Integrity() {
    }

    static String of(Path file) {
        MessageDigest digest = sha512();
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        return PREFIX + Base64.getEncoder().encodeToString(digest.digest());
    }

    private static MessageDigest sha512() {
        try {
            return MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 is unavailable", e);
        }
    }
}
