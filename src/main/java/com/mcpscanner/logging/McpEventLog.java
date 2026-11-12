package com.mcpscanner.logging;

import burp.api.montoya.logging.Logging;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class McpEventLog {

    public enum Level { DEBUG, INFO, WARN, ERROR }

    private static final int MAX_LINES = 1000;
    private static final McpEventLog NOOP = new NoopEventLog();

    /** Returns a singleton no-op instance whose write methods silently discard all messages. */
    public static McpEventLog noop() {
        return NOOP;
    }

    private final RetainedLogEntries entries = new RetainedLogEntries(MAX_LINES);
    private final List<Consumer<LogEntry>> listeners = new CopyOnWriteArrayList<>();
    private final Logging burpLogging;
    private final ExecutorService fanoutExecutor;

    public McpEventLog(Logging burpLogging) {
        this(burpLogging, defaultFanoutExecutor());
    }

    McpEventLog(Logging burpLogging, ExecutorService fanoutExecutor) {
        this.burpLogging = burpLogging;
        this.fanoutExecutor = fanoutExecutor;
    }

    private McpEventLog() {
        this.burpLogging = null;
        this.fanoutExecutor = null;
    }

    private static ExecutorService defaultFanoutExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread t = new Thread(r, "McpEventLog-fanout");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.DiscardPolicy());
    }

    public void debug(String message) {
        append(Level.DEBUG, message, null);
    }

    public void info(String message) {
        append(Level.INFO, message, null);
    }

    public void kept(String message) {
        append(new LogEntry(Instant.now(), Level.INFO, message, null, true));
    }

    public void warn(String message) {
        append(Level.WARN, message, null);
    }

    public void error(String message) {
        append(Level.ERROR, message, null);
    }

    public void error(String message, Throwable t) {
        append(Level.ERROR, message, t);
    }

    int listenerCount() {
        return listeners.size();
    }

    public List<LogEntry> subscribe(Consumer<LogEntry> listener) {
        synchronized (entries) {
            listeners.add(listener);
            return entries.snapshot();
        }
    }

    public void unsubscribe(Consumer<LogEntry> listener) {
        listeners.remove(listener);
    }

    public List<LogEntry> snapshot() {
        synchronized (entries) {
            return entries.snapshot();
        }
    }

    public void shutdown() {
        fanoutExecutor.shutdownNow();
        listeners.clear();
    }

    private void append(Level level, String message, Throwable throwable) {
        append(new LogEntry(Instant.now(), level, message, throwable, false));
    }

    private void append(LogEntry entry) {
        synchronized (entries) {
            entries.add(entry);
            List<Consumer<LogEntry>> recipients = List.copyOf(listeners);
            fanoutExecutor.execute(() -> notifyListeners(recipients, entry));
        }
        mirrorToBurp(entry);
    }

    private void notifyListeners(List<Consumer<LogEntry>> recipients, LogEntry entry) {
        for (Consumer<LogEntry> listener : recipients) {
            deliverGuarded(listener, entry);
        }
    }

    private void deliverGuarded(Consumer<LogEntry> listener, LogEntry entry) {
        try {
            listener.accept(entry);
        } catch (RuntimeException e) {
            if (burpLogging != null) {
                burpLogging.logToError("McpEventLog listener threw for " + entry.level() + " entry", e);
            }
        }
    }

    private void mirrorToBurp(LogEntry entry) {
        if (burpLogging == null || entry.level() == Level.DEBUG) {
            return;
        }
        if (entry.level() == Level.INFO) {
            burpLogging.logToOutput(entry.message());
        } else if (entry.throwable() != null) {
            burpLogging.logToError(entry.message(), entry.throwable());
        } else {
            burpLogging.logToError(entry.message());
        }
    }

    public record LogEntry(Instant timestamp, Level level, String message, Throwable throwable, boolean kept) {}

    private static final class NoopEventLog extends McpEventLog {
        private NoopEventLog() {}

        @Override public void debug(String message) {}
        @Override public void info(String message) {}
        @Override public void kept(String message) {}
        @Override public void warn(String message) {}
        @Override public void error(String message) {}
        @Override public void error(String message, Throwable t) {}
        @Override public List<LogEntry> subscribe(Consumer<LogEntry> listener) { return List.of(); }
        @Override public void shutdown() {}
    }
}
