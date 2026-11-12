package com.mcpscanner.cvetargets.environment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ProcessCommandRunner implements CommandRunner {

    private static final int TIMED_OUT_EXIT_CODE = -1;

    @Override
    public CommandResult run(List<String> command, Path workingDirectory, Duration timeout) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly().waitFor();
                return new CommandResult(command, TIMED_OUT_EXIT_CODE, "timed out after " + timeout);
            }
            return new CommandResult(command, process.exitValue(), output.join());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot run " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EnvironmentException("Interrupted while running " + command);
        }
    }

    private static String readAll(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
