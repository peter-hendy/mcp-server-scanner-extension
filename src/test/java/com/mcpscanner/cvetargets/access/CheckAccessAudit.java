package com.mcpscanner.cvetargets.access;

import burp.api.montoya.http.Http;
import com.mcpscanner.automation.ProbeCounter;
import com.mcpscanner.cvetargets.manifest.AccessItem;
import com.mcpscanner.cvetargets.mining.ArmMiner;
import com.mcpscanner.cvetargets.mining.ArmUsers;
import com.mcpscanner.cvetargets.mining.MiningConnection;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.requests.BaseRequestScope;
import com.mcpscanner.cvetargets.requests.BaseRequestSet;
import com.mcpscanner.cvetargets.requests.DiscoveredInventoryRequestSource;
import com.mcpscanner.cvetargets.requests.VerbatimHeaderHttp;
import com.mcpscanner.cvetargets.run.ArmAudit;
import com.mcpscanner.cvetargets.run.ArmObservation;
import com.mcpscanner.cvetargets.run.ArmProbeService;
import com.mcpscanner.cvetargets.run.ArmProgress;
import com.mcpscanner.cvetargets.run.ArmRun;
import com.mcpscanner.cvetargets.run.ArmTeardown;
import com.mcpscanner.cvetargets.run.AuditedArm;
import com.mcpscanner.cvetargets.run.IssueSummary;
import com.mcpscanner.cvetargets.run.ProbeTally;
import com.mcpscanner.cvetargets.run.TornDownArmHttp;

import java.util.List;
import java.util.stream.Collectors;

public final class CheckAccessAudit implements ArmAudit {

    public static AuditedArm arm() {
        return new AuditedArm(new CheckAccessAudit(), new ArmProbeService());
    }

    @Override
    public String mode() {
        return "access";
    }

    @Override
    public ArmObservation audit(ArmRun run, ArmEndpoint endpoint, ArmTeardown teardown, ArmProgress progress) {
        Http http = new VerbatimHeaderHttp(run.target().manifest().readTimeout());
        MiningConnection connection = new MiningConnection();
        try (BaseRequestSet base = new DiscoveredInventoryRequestSource(connection).open(endpoint,
                BaseRequestScope.unlimited(run.identities().routed(run.target().manifest().baseSeeds())), http)) {
            ProbeCounter probes = new ProbeCounter(base::reachedTarget);
            Http routed = probes.wrap(new TornDownArmHttp(base.route(http), teardown));
            ArmUsers users = ArmUsers.of(run.identities(), run::auth);
            new ArmMiner(connection, routed).mineInto(users);
            List<IssueSummary> issues = new ArmCheckAccess(run, users.roster(), rows(run, connection)).check(routed)
                    .stream()
                    .map(IssueSummary::of).toList();
            return new ArmObservation(issues, base.requests().size(), base.unansweredCount(),
                    new ProbeTally(probes.sent(), probes.reached()), null);
        }
    }

    private static ArmAccessRows rows(ArmRun run, MiningConnection connection) {
        return new ArmAccessRows(run.target().manifest().identities().items().stream()
                .collect(Collectors.toMap(AccessItem::key, AccessItem::seed, (first, second) -> first)), connection);
    }
}
