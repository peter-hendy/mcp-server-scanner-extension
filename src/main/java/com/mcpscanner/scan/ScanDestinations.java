package com.mcpscanner.scan;

import burp.api.montoya.burpsuite.BurpSuite;
import burp.api.montoya.scanner.Scanner;
import com.mcpscanner.logging.McpEventLog;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class ScanDestinations {

    private final Map<ScanMode, ScanDestination> byMode;

    public ScanDestinations(Map<ScanMode, ScanDestination> byMode) {
        this.byMode = new EnumMap<>(byMode);
        if (!this.byMode.keySet().containsAll(EnumSet.allOf(ScanMode.class))) {
            throw new IllegalArgumentException("Every scan mode needs a destination, got " + byMode.keySet());
        }
    }

    public static ScanDestinations of(Scanner scanner, BurpSuite burpSuite, McpEventLog eventLog,
                                      EngineScans engineScans, Runnable auditStarting) {
        return new ScanDestinations(Map.of(
                ScanMode.MCP_ONLY, new EngineScanDestination(engineScans),
                ScanMode.MCP_AND_BURP,
                new BurpAuditDestination(scanner, new BurpTaskExecution(burpSuite), eventLog, auditStarting)));
    }

    ScanDestination forMode(ScanMode mode) {
        return byMode.get(mode);
    }
}
