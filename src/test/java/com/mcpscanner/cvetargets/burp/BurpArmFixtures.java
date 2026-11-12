package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.api.CheckDescriptor;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.checks.api.RequestCheck;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.ManifestFixtures;
import com.mcpscanner.cvetargets.run.ArmRun;

import java.nio.file.Path;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class BurpArmFixtures {

    static final String CHECK = "example-check";

    private BurpArmFixtures() {
    }

    static ArmRun vulnerableArm(Path root, AuthStrategy auth) {
        String manifest = ManifestFixtures.VALID_MANIFEST.formatted("seeded-target", CHECK).replace(
                "\"auth\": { \"scheme\": \"none\" },",
                "\"auth\": { \"scheme\": \"none\" }, \"seeds\": [ { \"method\": \"tools/call\", "
                        + "\"params\": { \"name\": \"read\", \"arguments\": { \"path\": \"a.md\" } } } ],");
        ManifestFixtures.writeTarget(root, CHECK, "seeded-target", manifest);
        LoadedTarget target = ManifestFixtures.loadOnly(root, CHECK, "seeded-target");
        return ArmRun.asDeclared(target, target.manifest().arms().get(0), check(), auth);
    }

    static ArmRun fixedArm(Path root, AuthStrategy auth) {
        ArmRun vulnerable = vulnerableArm(root, auth);
        return ArmRun.asDeclared(vulnerable.target(), vulnerable.target().manifest().arms().get(1), vulnerable.check(),
                auth);
    }

    private static McpCheck check() {
        McpCheck check = mock(RequestCheck.class);
        CheckDescriptor descriptor = mock(CheckDescriptor.class);
        when(descriptor.id()).thenReturn(CHECK);
        when(check.descriptor()).thenReturn(descriptor);
        return check;
    }
}
