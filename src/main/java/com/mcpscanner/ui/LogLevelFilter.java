package com.mcpscanner.ui;

import com.mcpscanner.logging.McpEventLog.Level;

import java.util.Optional;

enum LogLevelFilter {
    ALL("All", null),
    INFO("Info", Level.INFO),
    WARNINGS("Warnings", Level.WARN),
    ERRORS("Errors", Level.ERROR),
    DEBUG("Debug", Level.DEBUG);

    private final String label;
    private final Level level;

    LogLevelFilter(String label, Level level) {
        this.label = label;
        this.level = level;
    }

    boolean accepts(Level candidate) {
        return Optional.ofNullable(level).map(candidate::equals).orElse(candidate != Level.DEBUG);
    }

    @Override
    public String toString() {
        return label;
    }
}
