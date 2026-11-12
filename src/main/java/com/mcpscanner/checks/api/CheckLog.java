package com.mcpscanner.checks.api;

import com.mcpscanner.checks.shared.scancontext.CurrentScan;
import com.mcpscanner.logging.McpEventLog;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class CheckLog {

    private final McpEventLog eventLog;
    private final String prefix;
    private final Set<SkipReason> loggedThisLoad = ConcurrentHashMap.newKeySet();

    public CheckLog(McpEventLog eventLog, String checkId) {
        this(prefix(checkId), eventLog);
    }

    private CheckLog(String prefix, McpEventLog eventLog) {
        this.eventLog = eventLog;
        this.prefix = prefix;
    }

    public static CheckLog labelled(McpEventLog eventLog, String label) {
        return new CheckLog(label + " — ", eventLog);
    }

    public static String prefix(String checkId) {
        return "Scan check: " + checkId + " — ";
    }

    public void debug(String message) {
        eventLog.debug(prefix + message);
    }

    public void info(String message) {
        eventLog.info(prefix + message);
    }

    public void warn(String message) {
        eventLog.warn(prefix + message);
    }

    public void notRun(String subject, SkipReason reason) {
        firstAtInfo(CurrentScan::firstNotice, reason.text(), reason.line(subject));
    }

    public void firstAtInfo(Predicate<String> firstNotice, String notice, String line) {
        if (firstNotice.test(prefix + notice)) {
            info(line);
        } else {
            debug(line);
        }
    }

    public void notRunOncePerLoad(String subject, SkipReason reason) {
        if (loggedThisLoad.add(reason)) {
            info(reason.line(subject));
        }
    }
}
