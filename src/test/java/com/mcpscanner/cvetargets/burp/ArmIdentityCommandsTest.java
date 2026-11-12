package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.automation.CommandAuth;
import com.mcpscanner.automation.CommandIdentities;
import com.mcpscanner.automation.CommandUser;
import com.mcpscanner.automation.ExpectedAccess;
import com.mcpscanner.automation.ScanAllCommand;
import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.cvetargets.identities.ArmIdentities;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.ManifestFixtures;
import com.mcpscanner.cvetargets.requests.ArmEndpoint;
import com.mcpscanner.cvetargets.run.ArmRun;
import com.mcpscanner.identity.IdentityRole;
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

class ArmIdentityCommandsTest {

    private static final URI ENDPOINT = URI.create("http://127.0.0.1:41000/mcp");
    private static final String IDENTITIES = """
            "identities": {
              "primary": { "name": "alice" },
              "others": [ { "id": "bob", "role": "peer", "headers": { "Authorization": "Bearer bob" } } ],
              "items": [
                { "seed": { "method": "tools/call", "params": { "name": "get_note", "arguments": { "note_id": 7 } } } },
                { "seed": { "method": "tools/call", "params": { "name": "list_notes", "arguments": {} } },
                  "expectation": "shared_by_design" },
                { "seed": { "method": "resources/read", "params": { "uri": "notes://team" } },
                  "expectation": "owner_only" } ]
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
    void handsBurpTheBoundRosterAsUsersToConnectAsAndCompare() {
        CommandIdentities handed = scanAll().identities();

        assertThat(handed.connectAs()).isEqualTo(new CommandUser("alice", null, null, "ALICE_ONLY",
                Map.of("note_id", "a-1")));
        assertThat(handed.others()).containsExactly(new CommandUser("bob", IdentityRole.PEER,
                new CommandAuth("minted-bob", null), "BOB_ONLY", Map.of("note_id", "b-1")));
    }

    @Test
    void confirmsTheItemToolsAndDeclaresTheirExpectationsAsAUserWould() {
        CommandIdentities handed = scanAll().identities();

        assertThat(handed.confirmedWrites()).isEqualTo(Set.of("get_note", "list_notes"));
        assertThat(handed.expectations()).containsExactlyInAnyOrder(
                new ExpectedAccess(new AccessItemKey(AccessItemKey.Kind.TOOL, "list_notes"),
                        AccessExpectation.SHARED_BY_DESIGN),
                new ExpectedAccess(new AccessItemKey(AccessItemKey.Kind.RESOURCE, "notes://team"),
                        AccessExpectation.OWNER_ONLY));
    }

    @Test
    void handsOverEveryItemSeedSoBurpSendsTheItemsExactArguments() {
        assertThat(scanAll().identities().itemSeeds())
                .extracting(seed -> seed.method() + " " + seed.params())
                .containsExactly("tools/call {\"name\":\"get_note\",\"arguments\":{\"note_id\":7}}",
                        "tools/call {\"name\":\"list_notes\",\"arguments\":{}}",
                        "resources/read {\"uri\":\"notes://team\"}");
    }

    @Test
    void scansTheItemSeedsBesideTheInventoryAsDirectModeDoes() {
        assertThat(scanAll().seeds()).extracting(seed -> seed.params().path("name").asText()
                + seed.params().path("uri").asText()).containsExactly("get_note", "list_notes", "notes://team");
    }

    @Test
    void aSeedSentAsAnotherIdentityIsNotRunnableInBurp() {
        LoadedTarget target = withIdentities(IDENTITIES.replace("\"params\": { \"uri\": \"notes://team\" } }",
                "\"params\": { \"uri\": \"notes://team\" }, \"asIdentity\": \"bob\" }"));
        ArmRun run = ArmRun.asDeclared(target, target.manifest().arms().get(0),
                BurpArmFixtures.vulnerableArm(root, new NoAuthStrategy()).check(), new NoAuthStrategy());

        assertThat(new ScanAllCommands(false, BurpAuditConfiguration.EXTENSIONS_ONLY).notRunnable(run))
                .hasValueSatisfying(reason -> assertThat(reason).contains("another identity"));
    }

    @Test
    void anArmWithoutDeclaredIdentitiesHandsNoneOver() {
        ScanAllCommand command = new ScanAllCommands(false, BurpAuditConfiguration.EXTENSIONS_ONLY)
                .forArm(BurpArmFixtures.vulnerableArm(root, new NoAuthStrategy()), endpoint());

        assertThat(command.identities()).isEqualTo(CommandIdentities.NONE);
    }

    private ScanAllCommand scanAll() {
        LoadedTarget target = withIdentities(IDENTITIES);
        ArmRun run = ArmRun.asDeclared(target, target.manifest().arms().get(0),
                BurpArmFixtures.vulnerableArm(root, new NoAuthStrategy()).check(),
                new BearerTokenAuthStrategy("alice"), identities, McpEventLog.noop());
        return new ScanAllCommands(false, BurpAuditConfiguration.EXTENSIONS_ONLY).forArm(run, endpoint());
    }

    private static ArmEndpoint endpoint() {
        return new ArmEndpoint(ENDPOINT, new NoAuthStrategy(), TransportType.STREAMABLE_HTTP);
    }

    private LoadedTarget withIdentities(String identities) {
        String manifest = ManifestFixtures.VALID_MANIFEST.formatted("with-identities", BurpArmFixtures.CHECK)
                .replace("\"arms\": [", identities + "  \"arms\": [");
        ManifestFixtures.writeTarget(root, BurpArmFixtures.CHECK, "with-identities", manifest);
        return ManifestFixtures.loadOnly(root, BurpArmFixtures.CHECK, "with-identities");
    }
}
