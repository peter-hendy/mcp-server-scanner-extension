package com.mcpscanner.cvetargets.burp;

import burp.api.montoya.scanner.audit.issues.AuditIssueConfidence;
import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.automation.CheckAccessCommand;
import com.mcpscanner.automation.CheckAccessPhase;
import com.mcpscanner.automation.CheckAccessStatus;
import com.mcpscanner.automation.ReportedIssue;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.cvetargets.environment.Pause;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.run.ArmBoundAuth;
import com.mcpscanner.cvetargets.run.ArmObservation;
import com.mcpscanner.cvetargets.run.ArmProgress;
import com.mcpscanner.cvetargets.run.ArmTeardown;
import com.mcpscanner.cvetargets.run.ProbeTally;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

class BurpCheckAccessAuditTest {

    private static final URI ENDPOINT = URI.create("http://127.0.0.1:41000/mcp");
    private static final ReportedIssue CROSS_USER = new ReportedIssue(
            "MCP operation result available to another principal", "HIGH", "FIRM", "http://127.0.0.1:41000",
            "detail", true, List.of());

    @TempDir
    Path root;

    private final List<String> origins = new ArrayList<>();
    private boolean closed;
    private CheckAccessStatus ended = status(CheckAccessPhase.FINISHED, List.of(CROSS_USER), null);
    private FakeBurpAutomation automation = new FakeBurpAutomation(() -> Optional.of(ended));

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void judgesTheIssuesCheckAccessFiledAndTheRequestsThatReachedTheTarget() {
        ArmObservation observation = audit(new ArmProgress());

        assertThat(observation.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.name()).isEqualTo("MCP operation result available to another principal");
            assertThat(issue.severity()).isEqualTo(AuditIssueSeverity.HIGH);
            assertThat(issue.confidence()).isEqualTo(AuditIssueConfidence.FIRM);
        });
        assertThat(observation.probes()).isEqualTo(new ProbeTally(12, 11));
        assertThat(closed).isTrue();
    }

    @Test
    void launchesCheckAccessAtTheArmsEndpointInABurpForItsOrigin() {
        audit(new ArmProgress());

        assertThat(origins).containsExactly("http://127.0.0.1:41000");
        assertThat(automation.launched()).singleElement().extracting(CheckAccessCommand::endpoint)
                .isEqualTo(ENDPOINT.toString());
    }

    @Test
    void retriesALaunchThatFailedToConnect() {
        automation = new FakeBurpAutomation(() -> Optional.of(ended), 2);

        audit(new ArmProgress());

        assertThat(automation.launched()).hasSize(3);
    }

    @Test
    void aCheckAccessThatDidNotFinishIsNotEvidenceButLeavesItsLastStatusAsProgress() {
        ended = status(CheckAccessPhase.STOPPED, List.of(CROSS_USER), "the server answered HTTP 429");
        ArmProgress progress = new ArmProgress();

        assertThatThrownBy(() -> audit(progress)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stopped").hasMessageContaining("HTTP 429");

        assertThat(closed).isTrue();
        assertThat(progress.latest()).hasValueSatisfying(observed ->
                assertThat(observed.issues()).hasSize(1));
    }

    @Test
    void reportsAHarnessObtainedOAuthArmAsNotRunnable() {
        AuthStrategy oauth = mock(AuthStrategy.class, withSettings().extraInterfaces(ArmBoundAuth.class));

        assertThat(burpAudit().notRunnable(BurpArmFixtures.vulnerableArm(root, oauth))).isPresent();
    }

    @Test
    void givesAnArmAtLeastTheBurpMinimumTime() {
        assertThat(burpAudit().armTimeout(Duration.ofMinutes(20))).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void reportsItsModeAsBurp() {
        assertThat(burpAudit().mode()).isEqualTo("burp");
    }

    private ArmObservation audit(ArmProgress progress) {
        return burpAudit().audit(BurpArmFixtures.vulnerableArm(root, new NoAuthStrategy()),
                new ArmEndpoint(ENDPOINT, new NoAuthStrategy(), TransportType.STREAMABLE_HTTP), new ArmTeardown(),
                progress);
    }

    private BurpCheckAccessAudit burpAudit() {
        return new BurpCheckAccessAudit((label, origin, abandoned) -> {
            origins.add(origin);
            return session();
        }, new CheckAccessCommands(false),
                new CheckAccessCompletion(Pause.sleeping(), Clock.systemUTC(), Duration.ZERO, Duration.ofMinutes(1)),
                Duration.ofHours(2));
    }

    private BurpSession session() {
        return new BurpSession() {
            @Override
            public BurpAutomation automation() {
                return automation;
            }

            @Override
            public void close() {
                closed = true;
            }
        };
    }

    private static CheckAccessStatus status(CheckAccessPhase phase, List<ReportedIssue> issues, String reason) {
        return new CheckAccessStatus(phase, 2, 2, 12, 11, issues, reason);
    }
}
