package com.mcpscanner.engine;

import com.mcpscanner.logging.Counted;
import com.mcpscanner.logging.McpEventLog;

final class EngineLog {

    private static final String PREFIX = "Extension scan ";

    private final McpEventLog eventLog;

    EngineLog(McpEventLog eventLog) {
        this.eventLog = eventLog;
    }

    static String count(int amount, String noun) {
        return Counted.of(amount, noun);
    }

    void debug(String message) {
        eventLog.debug(PREFIX + message);
    }

    void info(String message) {
        eventLog.info(PREFIX + message);
    }

    void kept(String message) {
        eventLog.kept(PREFIX + message);
    }

    void warn(String message) {
        eventLog.warn(PREFIX + message);
    }

    void error(String message, Throwable failure) {
        eventLog.error(PREFIX + message, failure);
    }
}
