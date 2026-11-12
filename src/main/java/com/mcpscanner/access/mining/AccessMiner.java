package com.mcpscanner.access.mining;

import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.scan.ScanInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class AccessMiner {

    private final MiningSender sender;
    private final MiningSettings settings;

    public AccessMiner(MiningSender sender, MiningSettings settings) {
        this.sender = sender;
        this.settings = settings;
    }

    public MiningResult mine(ScanInventory inventory, List<Principal> identities, MiningControl control) {
        MiningCatalog catalog = MiningCatalog.of(inventory);
        IdentityMiner miner = new IdentityMiner(sender, settings,
                new CallPlanner(catalog, new ToolGate(settings.allowedTools())), new ReplySightings(catalog), control);
        List<IdentityRun> runs = new ArrayList<>();
        MiningStop stop = MiningStop.COMPLETED;
        for (Principal identity : withAnonymous(identities)) {
            IdentityRun run = miner.mine(identity);
            runs.add(run);
            stop = run.stop();
            if (stop != MiningStop.COMPLETED) {
                break;
            }
        }
        VisibilityClassifier classifier = new VisibilityClassifier(runs);
        return new MiningResult(classifier.identities(), classifier.warnings(), stop);
    }

    private static List<Principal> withAnonymous(List<Principal> identities) {
        boolean included = identities.stream().anyMatch(identity -> Principal.isAnonymous(identity.id()));
        return included ? identities : Stream.concat(identities.stream(), Stream.of(Principal.anonymous())).toList();
    }
}
