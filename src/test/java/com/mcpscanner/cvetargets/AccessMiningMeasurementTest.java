package com.mcpscanner.cvetargets;

import com.mcpscanner.cvetargets.access.CheckAccessSubject;
import com.mcpscanner.access.mining.MiningSettings;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.cvetargets.identities.ArmIdentities;
import com.mcpscanner.cvetargets.identities.IdentityScripts;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.TargetArm;
import com.mcpscanner.cvetargets.manifest.TargetRepository;
import com.mcpscanner.cvetargets.mining.MiningMeasurementReport;
import com.mcpscanner.cvetargets.run.ArmResult;
import com.mcpscanner.cvetargets.run.ArmRun;
import com.mcpscanner.cvetargets.run.ArmSchedule;
import com.mcpscanner.cvetargets.run.CveTargetHarness;
import com.mcpscanner.cvetargets.run.HarnessSettings;
import com.mcpscanner.cvetargets.run.MiningAudit;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("cve-target")
@EnabledIfSystemProperty(named = AccessMiningMeasurementTest.MINE_PROPERTY, matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessMiningMeasurementTest {

    static final String MINE_PROPERTY = "cveTargets.mine";
    static final String UNANNOTATED_READS_PROPERTY = "cveTargets.mineUnannotated";

    private final HarnessSettings settings = HarnessSettings.fromSystemProperties();
    private final CveTargetHarness harness = CveTargetHarness.docker(settings,
            () -> MiningAudit.arm(MiningSettings.defaults(), Boolean.getBoolean(UNANNOTATED_READS_PROPERTY)));
    private final IdentityScripts identityScripts = IdentityScripts.processes();
    private final ArmSchedule schedule = new ArmSchedule(settings.parallelArms());
    private final List<CompletableFuture<ArmResult>> submitted = new CopyOnWriteArrayList<>();

    @AfterAll
    void writeTheMeasurement() {
        List<ArmResult> results = submitted.stream().map(CompletableFuture::join).toList();
        new MiningMeasurementReport(settings.resultsRoot().resolve(Boolean.getBoolean(UNANNOTATED_READS_PROPERTY)
                ? "mining-measurement-unannotated.md" : "mining-measurement.md")).write(results);
        schedule.close();
    }

    @TestFactory
    List<DynamicNode> everySelectedArmIsMinedAsEachIdentity() {
        List<LoadedTarget> targets = new TargetRepository(settings.targetsRoot()).load(settings.selection());
        assertThat(targets).as("targets selected by %s", settings.selection()).isNotEmpty();
        return targets.stream().<DynamicNode>map(target -> DynamicContainer.dynamicContainer(target.displayName(),
                target.manifest().arms().stream().map(arm -> mined(target, arm)).toList())).toList();
    }

    private DynamicNode mined(LoadedTarget target, TargetArm arm) {
        CompletableFuture<ArmResult> pending = schedule.submit(() -> run(target, arm))
                .exceptionally(failure -> ArmResult.crashed(target.manifest().check(), target, arm, failure));
        submitted.add(pending);
        return DynamicTest.dynamicTest(arm.name(), () ->
                assertThat(pending.join().error()).as("%s/%s", target.displayName(), arm.name()).isNull());
    }

    private ArmResult run(LoadedTarget target, TargetArm arm) {
        AuthStrategy auth = target.manifest().auth().including(arm.headers()).strategy();
        ArmIdentities identities = new ArmIdentities(target, arm.name(), auth, identityScripts);
        ArmRun run = ArmRun.asDeclared(target, arm, new CheckAccessSubject(target.manifest().check()), auth,
                identities, McpEventLog.noop());
        return harness.run(run, harness.newArm());
    }
}
