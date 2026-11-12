package com.mcpscanner.cvetargets.burp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

final class HeadlessBurpSession implements BurpSession {

    private static final long EXIT_GRACE_SECONDS = 15;
    private static final int LOG_TAIL_LINES = 20;

    private final Process process;
    private final Path workDir;
    private final HttpBurpAutomation automation;
    private final Thread killOnJvmExit;
    private final Path keptLog;

    HeadlessBurpSession(Process process, Path workDir, HttpBurpAutomation automation, Path keptLog) {
        this.keptLog = keptLog;
        this.process = process;
        this.workDir = workDir;
        this.automation = automation;
        this.killOnJvmExit = new Thread(this::kill, "headless-burp-exit");
        Runtime.getRuntime().addShutdownHook(killOnJvmExit);
    }

    @Override
    public HttpBurpAutomation automation() {
        return automation;
    }

    boolean automationAnswers() {
        return automation.answers();
    }

    boolean isAlive() {
        return process.isAlive();
    }

    String logTail() {
        try (Stream<String> lines = Files.lines(workDir.resolve(HeadlessBurpCommand.LOG_FILE))) {
            List<String> all = lines.filter(line -> !line.startsWith("\tat ")).toList();
            return String.join(" | ", all.subList(Math.max(0, all.size() - LOG_TAIL_LINES), all.size()));
        } catch (IOException | UncheckedIOException unreadable) {
            return "(no Burp log)";
        }
    }

    @Override
    public void close() {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        awaitExit();
        keepLog();
        WorkDirectory.delete(workDir);
        forgetExitHook();
    }

    private void keepLog() {
        try {
            Files.createDirectories(keptLog.getParent());
            Files.copy(workDir.resolve(HeadlessBurpCommand.LOG_FILE), keptLog, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException unreadable) {
            return;
        }
    }

    private void kill() {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private void forgetExitHook() {
        try {
            Runtime.getRuntime().removeShutdownHook(killOnJvmExit);
        } catch (IllegalStateException alreadyExiting) {
            return;
        }
    }

    private void awaitExit() {
        boolean interrupted = Thread.interrupted();
        try {
            if (!process.waitFor(EXIT_GRACE_SECONDS, TimeUnit.SECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly().waitFor(EXIT_GRACE_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException again) {
            process.destroyForcibly();
            interrupted = true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
