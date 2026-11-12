package com.mcpscanner.cvetargets.run;

import burp.api.montoya.http.Http;
import com.mcpscanner.access.mining.AccessMiner;
import com.mcpscanner.access.mining.MiningControl;
import com.mcpscanner.access.mining.MiningResult;
import com.mcpscanner.access.mining.MiningSettings;
import com.mcpscanner.access.mining.MiningWarning;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.cvetargets.mining.ArmMiningMeasurement;
import com.mcpscanner.cvetargets.mining.IdentityMeasurement;
import com.mcpscanner.cvetargets.mining.ArmMiner;
import com.mcpscanner.cvetargets.mining.MiningConnection;
import com.mcpscanner.cvetargets.mining.RecordingMiningSender;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.requests.BaseRequestScope;
import com.mcpscanner.cvetargets.requests.BaseRequestSet;
import com.mcpscanner.cvetargets.requests.DiscoveredInventoryRequestSource;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ToolSafety;
import com.mcpscanner.scan.ScanInventory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MiningAudit implements ArmAudit {

    private final MiningSettings settings;
    private final boolean unannotatedReads;

    public MiningAudit(MiningSettings settings, boolean unannotatedReads) {
        this.settings = settings;
        this.unannotatedReads = unannotatedReads;
    }

    public static AuditedArm arm(MiningSettings settings, boolean unannotatedReads) {
        return new AuditedArm(new MiningAudit(settings, unannotatedReads), new ArmProbeService());
    }

    @Override
    public String mode() {
        return "mining";
    }

    @Override
    public ArmObservation audit(ArmRun run, ArmEndpoint endpoint, ArmTeardown teardown, ArmProgress progress) {
        Http http = new VerbatimHeaderHttp(run.target().manifest().readTimeout());
        MiningConnection connection = new MiningConnection();
        try (BaseRequestSet base = new DiscoveredInventoryRequestSource(connection).open(endpoint,
                BaseRequestScope.unlimited(run.identities().routed(run.target().manifest().baseSeeds())), http)) {
            ArmMiningMeasurement measurement = mine(run, connection, new TornDownArmHttp(base.route(http), teardown));
            return new ArmObservation(List.of(), base.requests().size(), base.unansweredCount(), ProbeTally.NONE,
                    measurement);
        }
    }

    private ArmMiningMeasurement mine(ArmRun run, MiningConnection connection, Http http) {
        PrincipalRoster roster = run.identities().get();
        List<Principal> identities = roster.all();
        RecordingMiningSender sender = new RecordingMiningSender(
                new ArmMiner(connection, http).senderFor(run.identities()));
        AccessMiner miner = new AccessMiner(sender, settings(connection.inventory()));
        long started = System.nanoTime();
        MiningResult result = miner.mine(connection.inventory(), identities, MiningControl.unattended());
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        return measurement(connection.inventory(), identities, result, elapsedMillis, sender.calls());
    }

    private static ArmMiningMeasurement measurement(ScanInventory inventory, List<Principal> identities,
                                                    MiningResult result, long elapsedMillis,
                                                    List<RecordingMiningSender.SentCall> calls) {
        List<IdentityMeasurement> measured = result.identities().stream()
                .map(mining -> IdentityMeasurement.of(nameOf(mining.identityId(), identities), mining,
                        ownedBy(mining.identityId(), identities)))
                .toList();
        return new ArmMiningMeasurement(measured, result.stop().name(),
                result.warnings().stream().map(MiningWarning::kind).map(Enum::name).toList(), elapsedMillis,
                inventory.tools().size(), (int) inventory.tools().stream().filter(tool -> !ToolSafety.mayWrite(tool)).count(),
                inventory.resources().size(), inventory.resourceTemplates().size(), inventory.prompts().size(), calls);
    }

    private MiningSettings settings(ScanInventory inventory) {
        if (!unannotatedReads) {
            return settings;
        }
        return settings.withAllowedTools(inventory.tools().stream()
                .filter(tool -> tool.annotations().isAbsent() && !ToolSafety.nameSuggestsWrite(tool))
                .map(McpToolDefinition::name).collect(Collectors.toSet()));
    }

    private static String nameOf(String identityId, List<Principal> identities) {
        return identities.stream().filter(identity -> identity.id().equals(identityId)).map(Principal::name)
                .findFirst().orElse(identityId);
    }

    private static Map<String, String> ownedBy(String identityId, List<Principal> identities) {
        return identities.stream().filter(identity -> identity.id().equals(identityId)).map(identity -> identity.ownedObjects().all())
                .findFirst().orElse(Map.of());
    }
}
