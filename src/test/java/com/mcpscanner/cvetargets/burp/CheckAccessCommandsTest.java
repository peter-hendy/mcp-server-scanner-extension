package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.automation.CheckAccessCommand;
import com.mcpscanner.automation.CommandAuth;
import com.mcpscanner.automation.CommandUser;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.cvetargets.identities.ArmIdentities;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.ManifestFixtures;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.run.ArmRun;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckAccessCommandsTest {

    private static final URI ENDPOINT = URI.create("http://127.0.0.1:41000/mcp");
    private static final String IDENTITIES = """
            "identities": {
              "primary": { "name": "alice" },
              "others": [ { "id": "bob", "role": "peer", "headers": { "Authorization": "Bearer bob" } } ],
              "items": [
                { "seed": { "method": "tools/call", "params": { "name": "get_note", "arguments": {} } } } ]
            },
            """;

    @TempDir
    Path root;

    private final ArmIdentities identities = mock(ArmIdentities.class);

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
        when(identities.get()).thenReturn(PrincipalRoster.of(
                new Principal(Principal.PRIMARY_ID, "alice", PrincipalRole.PRIMARY, () -> Map.of(),
                        Optional.of("ALICE_ONLY"), Map.of("note_id", "a-1")),
                List.of(new Principal("bob", "bob", PrincipalRole.PEER,
                        () -> Map.of("Authorization", "Bearer minted-bob"), Optional.of("BOB_ONLY"),
                        Map.of("note_id", "b-1")))));
    }

    @Test
    void connectsAsThePrimaryWithItsCredentialAndHandsTheRosterOverLikeScanAll() {
        CheckAccessCommand command = new CheckAccessCommands(true).forArm(run(IDENTITIES), endpoint());

        assertThat(command.endpoint()).isEqualTo(ENDPOINT.toString());
        assertThat(command.transport()).isEqualTo(TransportType.STREAMABLE_HTTP);
        assertThat(command.auth()).isEqualTo(new CommandAuth("alice", Map.of()));
        assertThat(command.useBurpNetworking()).isTrue();
        assertThat(command.identities().others()).extracting(CommandUser::name).containsExactly("bob");
        assertThat(command.identities().confirmedWrites()).isEqualTo(Set.of("get_note"));
    }

    @Test
    void anItemOwnedByAnotherIdentityIsNotRunnableInBurp() {
        ArmRun run = run(IDENTITIES.replace("\"arguments\": {} } }", "\"arguments\": {} }, \"asIdentity\": \"bob\" }"));

        assertThat(new CheckAccessCommands(false).notRunnable(run))
                .hasValueSatisfying(reason -> assertThat(reason).contains("another identity"));
    }

    @Test
    void anArmWhoseItemsThePrimaryOwnsIsRunnable() {
        assertThat(new CheckAccessCommands(false).notRunnable(run(IDENTITIES))).isEmpty();
    }

    private ArmRun run(String identitiesJson) {
        String manifest = ManifestFixtures.VALID_MANIFEST.formatted("with-identities", BurpArmFixtures.CHECK)
                .replace("\"arms\": [", identitiesJson + "  \"arms\": [");
        ManifestFixtures.writeTarget(root, BurpArmFixtures.CHECK, "with-identities", manifest);
        LoadedTarget target = ManifestFixtures.loadOnly(root, BurpArmFixtures.CHECK, "with-identities");
        return ArmRun.asDeclared(target, target.manifest().arms().get(0),
                BurpArmFixtures.vulnerableArm(root, new NoAuthStrategy()).check(),
                new BearerTokenAuthStrategy("alice"), identities, McpEventLog.noop());
    }

    private static ArmEndpoint endpoint() {
        return new ArmEndpoint(ENDPOINT, new NoAuthStrategy(), TransportType.STREAMABLE_HTTP);
    }
}
