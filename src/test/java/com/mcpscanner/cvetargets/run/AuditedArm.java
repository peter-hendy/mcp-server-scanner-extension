package com.mcpscanner.cvetargets.run;

public record AuditedArm(ArmAudit audit, ArmProbeService probeService, ArmLateIssues lateIssues) {

    public AuditedArm(ArmAudit audit, ArmProbeService probeService) {
        this(audit, probeService, new ArmLateIssues());
    }
}
