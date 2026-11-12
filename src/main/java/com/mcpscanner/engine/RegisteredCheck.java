package com.mcpscanner.engine;

import burp.api.montoya.scanner.scancheck.ActiveScanCheck;
import burp.api.montoya.scanner.scancheck.ScanCheckType;

record RegisteredCheck(ActiveScanCheck check, ScanCheckType type, SkippedInvocations skips) {

    boolean perInsertionPoint() {
        return type == ScanCheckType.PER_INSERTION_POINT;
    }
}
