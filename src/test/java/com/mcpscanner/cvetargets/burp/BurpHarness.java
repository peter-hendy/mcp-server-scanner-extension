package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.cvetargets.environment.Pause;
import com.mcpscanner.cvetargets.run.ArmProbeService;
import com.mcpscanner.cvetargets.run.AuditedArm;
import com.mcpscanner.cvetargets.run.CveTargetHarness;
import com.mcpscanner.cvetargets.run.HarnessSettings;

import java.time.Clock;

public final class BurpHarness {

    private BurpHarness() {
    }

    public static CveTargetHarness docker(HarnessSettings harness, BurpSettings burp) {
        BurpLauncher launcher = launcher(burp);
        BurpScannerAudit audit = new BurpScannerAudit(launcher,
                new ScanAllCommands(burp.useBurpNetworking(), burp.configuration(), harness.allChecks()),
                new AuditCompletion(Pause.sleeping(), Clock.systemUTC(), burp.pollInterval(), burp.quietPeriod()),
                burp.configuration(), burp.minimumArmTimeout(), Clock.systemUTC());
        BurpCheckAccessAudit checkAccess = new BurpCheckAccessAudit(launcher,
                new CheckAccessCommands(burp.useBurpNetworking()),
                new CheckAccessCompletion(Pause.sleeping(), Clock.systemUTC(), burp.pollInterval(),
                        burp.minimumArmTimeout()),
                burp.minimumArmTimeout());
        return CveTargetHarness.docker(harness, () -> new AuditedArm(audit, new ArmProbeService()),
                () -> new AuditedArm(checkAccess, new ArmProbeService()));
    }

    private static BurpLauncher launcher(BurpSettings burp) {
        BurpLauncher cold = new HeadlessBurpLauncher(burp, Pause.sleeping());
        if (burp.armsPerBurp() <= 1 || !burp.configuration().divertsBuiltInChecks()) {
            return cold;
        }
        ReusedBurpLauncher reused = new ReusedBurpLauncher(cold, burp.armsPerBurp());
        Runtime.getRuntime().addShutdownHook(new Thread(reused::close, "headless-burp-reuse-exit"));
        return reused;
    }
}
