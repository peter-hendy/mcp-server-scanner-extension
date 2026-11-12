package com.mcpscanner.checks.api;

import burp.api.montoya.scanner.scancheck.ScanCheckType;

public enum CheckKind {
    INSERTION_POINT(ScanCheckType.PER_INSERTION_POINT, "Per insertion point"),
    REQUEST(ScanCheckType.PER_REQUEST, "Per request"),
    HOST(ScanCheckType.PER_REQUEST, "Per host");

    private final ScanCheckType scanCheckType;
    private final String label;

    CheckKind(ScanCheckType scanCheckType, String label) {
        this.scanCheckType = scanCheckType;
        this.label = label;
    }

    public ScanCheckType scanCheckType() {
        return scanCheckType;
    }

    public String label() {
        return label;
    }
}
