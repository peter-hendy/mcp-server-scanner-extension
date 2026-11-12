package com.mcpscanner.cvetargets.shared;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckFactory;
import com.mcpscanner.checks.api.CheckServices;
import com.mcpscanner.checks.api.RecordingChecks;
import burp.api.montoya.logging.Logging;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.cvetargets.environment.RunningArm;
import com.mcpscanner.cvetargets.identities.ArmIdentities;
import com.mcpscanner.cvetargets.manifest.TargetArm;
import com.mcpscanner.cvetargets.run.ArmContexts;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.ManifestFixtures;
import com.mcpscanner.cvetargets.manifest.ReadinessProbeType;
import com.mcpscanner.cvetargets.manifest.TargetRepository;
import com.mcpscanner.cvetargets.manifest.TargetSelection;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.run.ArmAudit;
import com.mcpscanner.cvetargets.run.ArmObservation;
import com.mcpscanner.cvetargets.run.ArmProgress;
import com.mcpscanner.cvetargets.run.ArmProbeService;
import com.mcpscanner.cvetargets.run.ArmResult;
import com.mcpscanner.cvetargets.run.ArmRun;
import com.mcpscanner.cvetargets.run.ArmRunner;
import com.mcpscanner.cvetargets.run.ArmTeardown;
import com.mcpscanner.cvetargets.run.AuditedArm;
import com.mcpscanner.cvetargets.run.CheckFindings;
import com.mcpscanner.cvetargets.run.CveTargetHarness;
import com.mcpscanner.cvetargets.run.IssueSummary;
import com.mcpscanner.cvetargets.run.ProbeTally;
import com.mcpscanner.cvetargets.run.ResultWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;

class SharedArmPlannerTest {

    private static final IssueSummary FOUND = new IssueSummary("Found", AuditIssueSeverity.HIGH,
            AuditIssueConfidence.FIRM, "http://127.0.0.1:1", "detail");

    @TempDir
    Path root;

    private final AtomicInteger starts = new AtomicInteger();
    private final List<String> audited = new ArrayList<>();
    private final List<ArmIdentities> auditedIdentities = new ArrayList<>();
    private final RecordingContexts contexts = new RecordingContexts();
    private Optional<String> refusal = Optional.empty();
    private List<String> catalogChecks = List.of("check-a", "check-b");

    @Test
    void auditsTheSharedBuildOnceAndJudgesEveryCatalogManifestFromIt() {
        List<ArmResult> results = planner().plan(sharedVulnerableArm()).get();

        assertThat(starts).hasValue(1);
        assertThat(audited).containsExactly("check-a/target-one");
        assertThat(results).extracting(ArmResult::check, ArmResult::target, ArmResult::passed, ArmResult::notRunnable)
                .containsExactly(tuple("check-z", "target-three", false, "check check-z is not in the catalog yet"),
                        tuple("check-a", "target-one", true, null),
                        tuple("check-b", "target-two", false, null));
        assertThat(results.get(2).otherCheckIssues()).containsEntry("check-a", List.of(FOUND));
        assertThat(Files.exists(root.resolve("results/check-z/target-three/vulnerable.json"))).isTrue();
        assertThat(Files.exists(root.resolve("results/check-b/target-two/vulnerable.json"))).isTrue();
    }

    @Test
    void auditsTheSharedBuildWithTheLeadsIdentitiesAndOneCheckLogForEveryMember() {
        List<ArmResult> results = planner().plan(sharedVulnerableArm()).get();

        assertThat(auditedIdentities).containsExactly(contexts.identities);
        assertThat(contexts.cataloguedWith).containsExactly(contexts.identities);
        assertThat(contexts.identitiesFor).containsExactly("target-one/vulnerable");
        assertThat(results).filteredOn(result -> result.notRunnable() == null)
                .allSatisfy(result -> assertThat(result.checkLog()).containsExactly("INFO shared audit"));
    }

    @Test
    void namesTheSharedAuditAfterOneRunnableMembersCheckAndTarget() {
        catalogChecks = List.of("check-b");

        planner().plan(sharedVulnerableArm()).get();

        assertThat(audited).containsExactly("check-b/target-two");
    }

    @Test
    void reportsEveryManifestNotRunnableWithoutStartingABuildTheAuditCannotRun() {
        refusal = Optional.of("cannot hand over an OAuth session");

        List<ArmResult> results = planner().plan(sharedVulnerableArm()).get();

        assertThat(starts).hasValue(0);
        assertThat(results).extracting(ArmResult::notRunnable).containsOnly("check check-z is not in the catalog yet",
                "cannot hand over an OAuth session");
        assertThat(results).noneMatch(ArmResult::passed);
    }

    private SharedTargetArm sharedVulnerableArm() {
        ManifestFixtures.writeValidTarget(root.resolve("targets"), "check-a", "target-one");
        ManifestFixtures.writeValidTarget(root.resolve("targets"), "check-b", "target-two");
        ManifestFixtures.writeValidTarget(root.resolve("targets"), "check-z", "target-three");
        List<LoadedTarget> targets = new TargetRepository(root.resolve("targets")).load(TargetSelection.everything());
        return SharedTargetArms.of(targets).get(0);
    }

    private SharedArmPlanner planner() {
        ArmAudit audit = new ArmAudit() {
            @Override
            public String mode() {
                return "burp";
            }

            @Override
            public ArmObservation audit(ArmRun run, ArmEndpoint endpoint, ArmTeardown teardown, ArmProgress progress) {
                audited.add(run.checkId() + "/" + run.target().manifest().id());
                auditedIdentities.add(run.identities());
                run.checkLog().info("shared audit");
                return new ArmObservation(List.of(FOUND), 2, 0, new ProbeTally(4, 4), null,
                        Map.of("check-a", new CheckFindings(List.of(FOUND), new ProbeTally(2, 2)),
                                "check-b", new CheckFindings(List.of(), new ProbeTally(2, 2))));
            }

            @Override
            public Optional<String> notRunnable(ArmRun run) {
                return refusal;
            }
        };
        CveTargetHarness harness = new CveTargetHarness(
                armAudit -> new ArmRunner((target, arm) -> {
                    starts.incrementAndGet();
                    return running();
                },Map.of(ReadinessProbeType.MCP_INITIALIZE, (endpoint, timeout) -> { }), armAudit,
                        Clock.systemUTC()),
                () -> new AuditedArm(audit, new ArmProbeService()), new ResultWriter(root.resolve("results")));
        return new SharedArmPlanner(harness, contexts);
    }

    private final class RecordingContexts implements ArmContexts {

        private final ArmIdentities identities = ArmIdentities.none();
        private final McpEventLog checkLog = new McpEventLog(mock(Logging.class));
        private final List<String> identitiesFor = new ArrayList<>();
        private final List<ArmIdentities> cataloguedWith = new ArrayList<>();

        @Override
        public ArmIdentities identities(LoadedTarget target, TargetArm arm, AuthStrategy auth) {
            identitiesFor.add(target.manifest().id() + "/" + arm.name());
            return identities;
        }

        @Override
        public McpEventLog checkLog() {
            return checkLog;
        }

        @Override
        public CheckCatalog catalog(McpEventLog log, AuthStrategy auth, ArmIdentities armIdentities,
                                    AuditedArm audited) {
            cataloguedWith.add(armIdentities);
            return CheckCatalog.of(catalogChecks.stream().map(SharedArmPlannerTest::factory).toList(),
                    CheckServices.standalone());
        }
    }

    private static RunningArm running() {
        return new RunningArm() {
            @Override
            public URI endpoint() {
                return URI.create("http://127.0.0.1:1/mcp");
            }

            @Override
            public void close() {
            }
        };
    }

    private static CheckFactory factory(String id) {
        return services -> new RecordingChecks.Request(id, List.of());
    }
}
