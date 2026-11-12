package com.mcpscanner.cvetargets.environment;

import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.TargetArm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public record ComposeProject(String name, Path composeFile, int hostPort, Path downloadsDirectory) {

    public static final String HOST_PORT_VARIABLE = "CVET_HOST_PORT";
    public static final String DOWNLOADS_VARIABLE = "CVET_DOWNLOADS";
    private static final String PREFIX = "cvet-";
    private static final int ORIGIN_TAG_BYTES = 4;

    public static ComposeProject forArm(LoadedTarget target, TargetArm arm) {
        Path targetsRoot = targetsRoot(target);
        String name = (PREFIX + originTag(targetsRoot) + "-" + target.manifest().check()
                + "-" + target.manifest().id() + "-" + arm.name())
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return new ComposeProject(name, target.composeFile(), freeLoopbackPort(),
                targetsRoot.resolve(".cache").resolve("downloads"));
    }

    public Path directory() {
        return composeFile.getParent();
    }

    public String environmentFileContent() {
        return HOST_PORT_VARIABLE + "=" + hostPort + "\n"
                + DOWNLOADS_VARIABLE + "=" + downloadsDirectory.toAbsolutePath() + "\n";
    }

    private static Path targetsRoot(LoadedTarget target) {
        return canonical(target.directory()).getParent().getParent();
    }

    private static Path canonical(Path directory) {
        try {
            return directory.toRealPath();
        } catch (IOException unresolvable) {
            return directory.toAbsolutePath().normalize();
        }
    }

    private static String originTag(Path targetsRoot) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(targetsRoot.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, ORIGIN_TAG_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static int freeLoopbackPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot allocate a loopback port", e);
        }
    }
}
