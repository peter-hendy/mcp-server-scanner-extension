package com.mcpscanner.cvetargets.shared;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.cvetargets.identities.ArmIdentities;
import com.mcpscanner.cvetargets.run.ArmContexts;
import com.mcpscanner.cvetargets.run.ArmResult;
import com.mcpscanner.cvetargets.run.ArmRun;
import com.mcpscanner.cvetargets.run.AuditedArm;
import com.mcpscanner.cvetargets.run.CveTargetHarness;
import com.mcpscanner.cvetargets.run.SharedArmRun;
import com.mcpscanner.logging.McpEventLog;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class SharedArmPlanner {

    private final CveTargetHarness harness;
    private final ArmContexts contexts;

    public SharedArmPlanner(CveTargetHarness harness, ArmContexts contexts) {
        this.harness = harness;
        this.contexts = contexts;
    }

    public Supplier<List<ArmResult>> plan(SharedTargetArm shared) {
        ManifestArm lead = shared.lead();
        AuthStrategy auth = lead.target().manifest().auth().including(lead.arm().headers()).strategy();
        ArmIdentities identities = contexts.identities(lead.target(), lead.arm(), auth);
        McpEventLog checkLog = contexts.checkLog();
        AuditedArm audited = harness.newArm();
        CheckCatalog catalog = contexts.catalog(checkLog, auth, identities, audited);
        List<ArmResult> missingChecks = shared.members().stream()
                .filter(member -> catalog.find(checkOf(member)).isEmpty())
                .map(member -> notRunnable(member, audited, "check " + checkOf(member) + " is not in the catalog yet"))
                .toList();
        List<ArmRun> runs = shared.members().stream()
                .flatMap(member -> catalog.find(checkOf(member))
                        .map(check -> ArmRun.asDeclared(member.target(), member.arm(), check, auth, identities, checkLog))
                        .stream())
                .toList();
        if (runs.isEmpty()) {
            checkLog.shutdown();
            return () -> missingChecks;
        }
        SharedTargetArm runnable = new SharedTargetArm(runs.stream().map(run -> new ManifestArm(run.target(), run.arm()))
                .toList());
        ArmRun first = runs.get(0);
        ArmRun build = new ArmRun(runnable.runnable(), runnable.arm(), first.check(), auth, first.expected(),
                identities, checkLog);
        Optional<String> refusal = audited.audit().notRunnable(build);
        return () -> Stream.concat(missingChecks.stream(), refusal
                .map(reason -> runs.stream().map(run -> notRunnable(run, audited, reason)).toList())
                .orElseGet(() -> runLogged(new SharedArmRun(build, runs), audited)).stream()).toList();
    }

    private List<ArmResult> runLogged(SharedArmRun shared, AuditedArm audited) {
        try {
            return harness.run(shared, audited);
        } finally {
            audited.lateIssues().close();
            shared.build().checkLog().shutdown();
        }
    }

    private static String checkOf(ManifestArm member) {
        return member.target().manifest().check();
    }

    private ArmResult notRunnable(ManifestArm member, AuditedArm audited, String reason) {
        return harness.record(ArmResult.notRunnable(checkOf(member), member.target(), member.arm(),
                audited.audit().mode(), reason));
    }

    private ArmResult notRunnable(ArmRun run, AuditedArm audited, String reason) {
        return notRunnable(new ManifestArm(run.target(), run.arm()), audited, reason);
    }
}
