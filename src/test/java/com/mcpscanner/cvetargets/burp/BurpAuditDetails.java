package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.ReportedIssue;
import com.mcpscanner.cvetargets.run.ArmProgressDetail;

import java.util.List;

public record BurpAuditDetails(String configuration, String status, CompletedAudit.AuditEnd end, int launchedRequests,
                               int requestCount, int errorCount, int insertionPointCount, int builtInIssueCount,
                               List<ReportedIssue> builtInIssues, long burpStartupMillis, long auditMillis)
        implements ArmProgressDetail {

    @Override
    public String progress() {
        return "Burp audit " + status + ": " + requestCount + " requests, " + errorCount + " errors, "
                + insertionPointCount + " insertion points";
    }
}
