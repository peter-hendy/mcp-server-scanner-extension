package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.AuditSnapshot;

record CompletedAudit(AuditSnapshot snapshot, AuditEnd end) {

    enum AuditEnd { FINISHED, QUIESCENT, UNFINISHED }
}
