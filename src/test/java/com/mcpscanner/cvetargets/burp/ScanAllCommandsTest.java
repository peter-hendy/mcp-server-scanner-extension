package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.automation.ScanAllCommand;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.cvetargets.manifest.TargetManifest;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.run.ArmBoundAuth;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

class ScanAllCommandsTest {

    private static final URI ENDPOINT = URI.create("http://127.0.0.1:41000/mcp");

    @TempDir
    Path root;

    private final ScanAllCommands commands = new ScanAllCommands(false, BurpAuditConfiguration.EXTENSIONS_ONLY);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void scansTheArmEndpointWithOnlyTheArmsCheckAndTheManifestSeeds() {
        ScanAllCommand command = forArm(new NoAuthStrategy());

        assertThat(command.endpoint()).isEqualTo(ENDPOINT.toString());
        assertThat(command.transport()).isEqualTo(TransportType.STREAMABLE_HTTP);
        assertThat(command.checks()).containsExactly(BurpArmFixtures.CHECK);
        assertThat(command.seeds()).singleElement().satisfies(seed -> {
            assertThat(seed.method()).isEqualTo("tools/call");
            assertThat(seed.params().path("name").asText()).isEqualTo("read");
        });
        assertThat(command.useBurpNetworking()).isFalse();
        assertThat(command.divertBuiltInChecks()).isTrue();
        assertThat(command.auth().strategy().headers()).isEmpty();
        assertThat(command.maxCatalogItems()).isEqualTo(TargetManifest.DEFAULT_MAX_CATALOG_ITEMS);
    }

    @Test
    void withBuiltinsLetsBurpsOwnChecksReachTheTarget() {
        ScanAllCommand command = new ScanAllCommands(false, BurpAuditConfiguration.WITH_BUILTINS)
                .forArm(BurpArmFixtures.vulnerableArm(root, new NoAuthStrategy()),
                        new ArmEndpoint(ENDPOINT, new NoAuthStrategy(), TransportType.STREAMABLE_HTTP));

        assertThat(command.divertBuiltInChecks()).isFalse();
    }

    @Test
    void aBearerCredentialIsConfiguredAsBearerAuthentication() {
        ScanAllCommand command = forArm(new BearerTokenAuthStrategy("jwt-1"));

        assertThat(command.auth().bearerToken()).isEqualTo("jwt-1");
        assertThat(command.auth().headers()).isEmpty();
    }

    @Test
    void anyOtherHeadersAreConfiguredAsCustomHeaders() {
        ScanAllCommand command = forArm(new CustomHeaderAuthStrategy(Map.of(
                "Authorization", "Bearer jwt-1", "X-Upstream", "http://upstream.test")));

        assertThat(command.auth().bearerToken()).isNull();
        assertThat(command.auth().headers()).containsEntry("X-Upstream", "http://upstream.test")
                .containsEntry("Authorization", "Bearer jwt-1");
    }

    @Test
    void refusesAHarnessObtainedOAuthSessionItCannotHandToBurp() {
        AuthStrategy oauth = mock(AuthStrategy.class, withSettings().extraInterfaces(ArmBoundAuth.class));

        assertThatThrownBy(() -> forArm(oauth)).isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("OAuth");
    }

    @Test
    void allChecksAsksBurpToEnableEveryCatalogCheck() {
        ScanAllCommand command = new ScanAllCommands(false, BurpAuditConfiguration.EXTENSIONS_ONLY, true)
                .forArm(BurpArmFixtures.vulnerableArm(root, new NoAuthStrategy()),
                        new ArmEndpoint(ENDPOINT, new NoAuthStrategy(), TransportType.STREAMABLE_HTTP));

        assertThat(command.checks()).containsExactly(ScanAllCommand.EVERY_CHECK);
    }

    @Test
    void saysAHarnessObtainedOAuthArmIsNotRunnableBeforeAnythingStarts() {
        AuthStrategy oauth = mock(AuthStrategy.class, withSettings().extraInterfaces(ArmBoundAuth.class));

        assertThat(commands.notRunnable(BurpArmFixtures.vulnerableArm(root, oauth))).hasValueSatisfying(reason ->
                assertThat(reason).contains("OAuth"));
        assertThat(commands.notRunnable(BurpArmFixtures.vulnerableArm(root, new NoAuthStrategy()))).isEmpty();
    }

    private ScanAllCommand forArm(AuthStrategy auth) {
        return commands.forArm(BurpArmFixtures.vulnerableArm(root, auth),
                new ArmEndpoint(ENDPOINT, auth, TransportType.STREAMABLE_HTTP));
    }
}
