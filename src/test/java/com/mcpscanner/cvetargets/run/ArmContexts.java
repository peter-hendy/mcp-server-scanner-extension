package com.mcpscanner.cvetargets.run;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.cvetargets.identities.ArmIdentities;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.TargetArm;
import com.mcpscanner.logging.McpEventLog;

public interface ArmContexts {

    ArmIdentities identities(LoadedTarget target, TargetArm arm, AuthStrategy auth);

    McpEventLog checkLog();

    CheckCatalog catalog(McpEventLog checkLog, AuthStrategy auth, ArmIdentities identities, AuditedArm audited);
}
