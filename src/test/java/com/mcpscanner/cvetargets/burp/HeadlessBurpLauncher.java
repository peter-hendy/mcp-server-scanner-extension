package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.cvetargets.environment.Pause;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class HeadlessBurpLauncher implements BurpLauncher {

    private static final Duration STARTUP_POLL = Duration.ofSeconds(1);
    private static final String LOGS = "logs";

    private final BurpSettings settings;
    private final Pause pause;

    public HeadlessBurpLauncher(BurpSettings settings, Pause pause) {
        this.settings = settings;
        this.pause = pause;
    }

    @Override
    public HeadlessBurpSession start(String label, String armOrigin, BooleanSupplier abandoned) {
        Path workDir = WorkDirectory.createUnder(settings.workRoot());
        HeadlessBurpCommand command = new HeadlessBurpCommand(settings, workDir, freeLoopbackPort());
        HeadlessBurpSession session = new HeadlessBurpSession(launch(command), workDir,
                new HttpBurpAutomation(command.automationPort()),
                settings.workRoot().resolve(LOGS).resolve(label.replaceAll("[^A-Za-z0-9.-]", "_") + ".log"));
        try {
            awaitAutomation(session, abandoned);
            return session;
        } catch (RuntimeException notReady) {
            session.close();
            throw notReady;
        }
    }

    private static Process launch(HeadlessBurpCommand command) {
        try {
            Files.writeString(command.workDir().resolve(HeadlessBurpCommand.USER_CONFIG_FILE), command.userConfig());
            Optional<String> projectConfig = command.projectConfig();
            if (projectConfig.isPresent()) {
                Files.writeString(command.workDir().resolve(HeadlessBurpCommand.PROJECT_CONFIG_FILE), projectConfig.get());
            }
            return new ProcessBuilder(command.arguments())
                    .directory(command.workDir().toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(command.workDir().resolve(HeadlessBurpCommand.LOG_FILE).toFile())
                    .start();
        } catch (IOException failure) {
            WorkDirectory.delete(command.workDir());
            throw new UncheckedIOException("cannot start headless Burp", failure);
        }
    }

    private void awaitAutomation(HeadlessBurpSession session, BooleanSupplier abandoned) {
        Instant deadline = Instant.now().plus(settings.startupTimeout());
        while (!session.automationAnswers()) {
            if (abandoned.getAsBoolean()) {
                throw new IllegalStateException("the arm was torn down while headless Burp was starting");
            }
            if (!session.isAlive()) {
                throw new IllegalStateException("headless Burp exited before its test automation listened: "
                        + session.logTail());
            }
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("headless Burp did not start its test automation within "
                        + settings.startupTimeout() + ": " + session.logTail());
            }
            pause.pause(STARTUP_POLL);
        }
    }

    private static int freeLoopbackPort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
