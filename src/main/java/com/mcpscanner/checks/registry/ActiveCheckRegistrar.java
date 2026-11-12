package com.mcpscanner.checks.registry;

import burp.api.montoya.scanner.scancheck.ActiveScanCheck;
import burp.api.montoya.scanner.scancheck.ScanCheckType;

@FunctionalInterface
public interface ActiveCheckRegistrar {

    void register(ActiveScanCheck check, ScanCheckType type);
}
