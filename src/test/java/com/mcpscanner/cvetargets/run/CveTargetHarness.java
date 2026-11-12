package com.mcpscanner.cvetargets.run;

import com.mcpscanner.cvetargets.access.CheckAccessAudit;
import com.mcpscanner.cvetargets.environment.DockerTargetEnvironment;
import com.mcpscanner.cvetargets.environment.MachineSlots;
import com.mcpscanner.cvetargets.environment.Pause;
import com.mcpscanner.cvetargets.environment.ProcessCommandRunner;
import com.mcpscanner.cvetargets.environment.SampledArmResources;
import com.mcpscanner.cvetargets.environment.SlotLimitedEnvironment;
import com.mcpscanner.cvetargets.environment.TargetEnvironment;
import com.mcpscanner.cvetargets.manifest.ReadinessProbeType;
import com.mcpscanner.cvetargets.manifest.TargetManifest;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.testutil.MontoyaTestFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public final class CveTargetHarness {

    private final Function<ArmAudit, ArmRunner> runners;
    private final Supplier<AuditedArm> audits;
    private final Supplier<AuditedArm> accessAudits;
    private final ResultWriter writer;

    public CveTargetHarness(Function<ArmAudit, ArmRunner> runners, Supplier<AuditedArm> audits, ResultWriter writer) {
        this(runners, audits, audits, writer);
    }

    public CveTargetHarness(Function<ArmAudit, ArmRunner> runners, Supplier<AuditedArm> audits,
                            Supplier<AuditedArm> accessAudits, ResultWriter writer) {
        this.runners = runners;
        this.audits = audits;
        this.accessAudits = accessAudits;
        this.writer = writer;
    }

    public static CveTargetHarness docker(HarnessSettings settings) {
        return docker(settings, CveTargetHarness::checkRunnerArm, CheckAccessAudit::arm);
    }

    public static CveTargetHarness docker(HarnessSettings settings, Supplier<AuditedArm> audits) {
        return docker(settings, audits, audits);
    }

    public static CveTargetHarness docker(HarnessSettings settings, Supplier<AuditedArm> audits,
                                          Supplier<AuditedArm> accessAudits) {
        MontoyaTestFactory.install();
        ProcessCommandRunner commands = new ProcessCommandRunner();
        TargetEnvironment environment = new SlotLimitedEnvironment(new DockerTargetEnvironment(commands),
                new MachineSlots(settings.slotsDirectory(), settings.slots(), Pause.sleeping(),
                        Duration.ofMinutes(1), System.err::println));
        Map<ReadinessProbeType, ReadinessProbe> readiness = Map.of(
                ReadinessProbeType.MCP_INITIALIZE, new PollingReadiness(new McpInitializeAttempt()),
                ReadinessProbeType.SSE_ENDPOINT, new PollingReadiness(new SseEndpointAttempt()));
        SampledArmResources resources = SampledArmResources.onThisHost(commands);
        return new CveTargetHarness(
                audit -> new ArmRunner(environment, readiness, audit, Clock.systemUTC(), resources,
                        ArmSupervision.onThisMachine()),
                audits, accessAudits, new ResultWriter(settings.resultsRoot()));
    }

    public AuditedArm newArm() {
        return audits.get();
    }

    public AuditedArm newArm(TargetManifest manifest) {
        return manifest.checkAccess() ? accessAudits.get() : newArm();
    }

    public ArmResult run(ArmRun run, AuditedArm arm) {
        return record(runners.apply(arm.audit()).run(run));
    }

    public List<ArmResult> run(SharedArmRun shared, AuditedArm arm) {
        return runners.apply(arm.audit()).run(shared).stream().map(this::record).toList();
    }

    public ArmResult record(ArmResult result) {
        writer.write(result);
        return result;
    }

    private static AuditedArm checkRunnerArm() {
        GeneratedBaseRequests baseRequests = GeneratedBaseRequests.create();
        CheckRunnerAudit audit = new CheckRunnerAudit(baseRequests.source(), VerbatimHeaderHttp::new,
                baseRequests.insertionPoints());
        return new AuditedArm(audit, audit.probeService(), audit.lateIssues());
    }
}
