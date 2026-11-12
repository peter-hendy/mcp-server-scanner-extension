package com.mcpscanner.scan;

public record ScanActivity(int burpAudits, int auditRequests, int mcpOnlyScans, int probesRun, int issues) {

    private static final ScanActivity IDLE = new ScanActivity(0, 0, 0, 0, 0);

    public static ScanActivity idle() {
        return IDLE;
    }

    public static ScanActivity burpAudit(int requests, int issues) {
        return new ScanActivity(1, requests, 0, 0, issues);
    }

    public static ScanActivity mcpOnlyScan(int probesRun, int issues) {
        return new ScanActivity(0, 0, 1, probesRun, issues);
    }

    public ScanActivity plus(ScanActivity other) {
        return new ScanActivity(burpAudits + other.burpAudits, auditRequests + other.auditRequests,
                mcpOnlyScans + other.mcpOnlyScans, probesRun + other.probesRun, issues + other.issues);
    }

    public boolean isIdle() {
        return burpAudits == 0 && mcpOnlyScans == 0;
    }
}
