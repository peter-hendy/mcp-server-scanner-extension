package com.mcpscanner.cvetargets;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.checks.api.AccessLedger;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.checks.api.PrincipalListings;
import com.mcpscanner.checks.api.McpCheck;
import com.mcpscanner.cvetargets.environment.ComposeProject;
import com.mcpscanner.cvetargets.environment.DockerCompose;
import com.mcpscanner.cvetargets.environment.ProcessCommandRunner;
import com.mcpscanner.cvetargets.identities.ArmIdentities;
import com.mcpscanner.cvetargets.identities.IdentityScripts;
import com.mcpscanner.cvetargets.lifecycle.McpReplySucceedsCheck;
import com.mcpscanner.cvetargets.lifecycle.McpReplySucceedsCheckFactory;
import com.mcpscanner.cvetargets.manifest.ExpectedOutcome;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.Outcome;
import com.mcpscanner.cvetargets.manifest.TargetArm;
import com.mcpscanner.cvetargets.manifest.TargetRepository;
import com.mcpscanner.cvetargets.run.ArmResult;
import com.mcpscanner.cvetargets.run.ArmRun;
import com.mcpscanner.cvetargets.run.CveTargetHarness;
import com.mcpscanner.cvetargets.run.HarnessSettings;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("cve-target-lifecycle")
class CveTargetHarnessLifecycleTest {

    private final HarnessSettings settings = HarnessSettings.fromSystemProperties();
    private final CveTargetHarness harness = CveTargetHarness.docker(settings);
    private final IdentityScripts identityScripts = IdentityScripts.processes();

    @TestFactory
    List<DynamicNode> lifecycleProbeReachesEveryArmOfEveryPortedTargetAndTearsItDown() {
        List<LoadedTarget> targets = new TargetRepository(settings.targetsRoot()).load(settings.selection());
        assertThat(targets).as("targets selected by %s", settings.selection()).isNotEmpty();
        return targets.stream()
                .flatMap(target -> target.manifest().arms().stream().<DynamicNode>map(arm ->
                        DynamicTest.dynamicTest(target.displayName() + "/" + arm.name(), () -> runProbe(target, arm))))
                .toList();
    }

    private void runProbe(LoadedTarget target, TargetArm arm) {
        AuthStrategy auth = target.manifest().auth().strategy();
        ArmIdentities identities = new ArmIdentities(target, arm.name(), auth, identityScripts);
        ArmResult result = harness.run(new ArmRun(target, arm, probe(auth), auth, ExpectedOutcome.of(Outcome.FIRE),
                identities, McpEventLog.noop()), harness.newArm());

        assertThat(result.error()).isNull();
        assertThat(result.baseRequestCount()).isPositive();
        assertThat(result.passed()).as(result.verdict()).isTrue();
        assertThat(composeFor(target, arm).containers()).isEmpty();
    }

    private static McpCheck probe(AuthStrategy auth) {
        CheckServices services = new CheckServices(McpEventLog.noop(), () -> auth, Optional::empty, Optional::empty,
                PrincipalRoster::empty, AccessLedger.noop(), PrincipalListings.unknown());
        return CheckCatalog.of(List.of(new McpReplySucceedsCheckFactory()), services)
                .find(McpReplySucceedsCheck.ID).orElseThrow();
    }

    private static DockerCompose composeFor(LoadedTarget target, TargetArm arm) {
        return new DockerCompose(new ProcessCommandRunner(),
                ComposeProject.forArm(target, arm));
    }
}
