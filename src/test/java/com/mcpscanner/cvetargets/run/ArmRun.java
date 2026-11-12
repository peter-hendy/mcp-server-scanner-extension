package com.mcpscanner.cvetargets.run;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.cvetargets.identities.ArmIdentities;
import com.mcpscanner.cvetargets.manifest.ExpectedOutcome;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.TargetArm;
import com.mcpscanner.logging.McpEventLog;

import java.util.List;

public record ArmRun(LoadedTarget target, TargetArm arm, McpCheck check, AuthStrategy auth, ExpectedOutcome expected,
                     ArmIdentities identities, McpEventLog checkLog) {

    public ArmRun(LoadedTarget target, TargetArm arm, McpCheck check, AuthStrategy auth, ExpectedOutcome expected) {
        this(target, arm, check, auth, expected, ArmIdentities.none(), McpEventLog.noop());
    }

    public static ArmRun asDeclared(LoadedTarget target, TargetArm arm, McpCheck check, AuthStrategy auth) {
        return new ArmRun(target, arm, check, auth, arm.expected());
    }

    public static ArmRun asDeclared(LoadedTarget target, TargetArm arm, McpCheck check, AuthStrategy auth,
                                    ArmIdentities identities, McpEventLog checkLog) {
        return new ArmRun(target, arm, check, auth, arm.expected(), identities, checkLog);
    }

    public List<String> checkLogLines() {
        return checkLog.snapshot().stream().map(entry -> entry.level() + " " + entry.message()).toList();
    }

    public String checkId() {
        return check.descriptor().id();
    }
}
