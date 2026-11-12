package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.CommandIdentities;
import com.mcpscanner.automation.CommandUser;
import com.mcpscanner.automation.ExpectedAccess;
import com.mcpscanner.automation.SeedCall;
import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.cvetargets.manifest.AccessItem;
import com.mcpscanner.cvetargets.manifest.IdentitySetup;
import com.mcpscanner.cvetargets.run.ArmRun;
import com.mcpscanner.identity.IdentityRole;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ArmIdentityCommands {

    private ArmIdentityCommands() {
    }

    static CommandIdentities of(ArmRun run) {
        IdentitySetup setup = run.target().manifest().identities();
        if (!setup.declared()) {
            return CommandIdentities.NONE;
        }
        PrincipalRoster roster = run.identities().get();
        return new CommandIdentities(connectAs(roster.primary()),
                roster.compared().stream().map(ArmIdentityCommands::compared).toList(),
                confirmedWrites(setup.items()), expectations(setup.items()), itemSeeds(setup.items()));
    }

    private static CommandUser connectAs(Principal primary) {
        return new CommandUser(primary.name(), null, null, primary.privateMarker().orElse(null),
                primary.ownedObjects().all());
    }

    private static CommandUser compared(Principal other) {
        return new CommandUser(other.name(), IdentityRole.valueOf(other.role().name()),
                ScanAllCommands.auth(other.credentialHeaders().get()), other.privateMarker().orElse(null),
                other.ownedObjects().all());
    }

    private static Set<String> confirmedWrites(List<AccessItem> items) {
        return items.stream().map(AccessItem::key).filter(key -> key.kind() == AccessItemKey.Kind.TOOL)
                .map(AccessItemKey::name).collect(Collectors.toUnmodifiableSet());
    }

    private static List<SeedCall> itemSeeds(List<AccessItem> items) {
        return items.stream().map(AccessItem::seed).map(seed -> new SeedCall(seed.method(), seed.params())).toList();
    }

    private static List<ExpectedAccess> expectations(List<AccessItem> items) {
        return items.stream().filter(item -> item.expectation() != null)
                .map(item -> new ExpectedAccess(item.key(), item.expectation())).toList();
    }
}
