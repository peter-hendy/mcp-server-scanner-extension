package com.mcpscanner.cvetargets.identities;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.api.AccessOutcome;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.cvetargets.environment.CommandResult;
import com.mcpscanner.cvetargets.environment.CommandRunner;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.ManifestFixtures;
import com.mcpscanner.cvetargets.manifest.ProvisionedToken;
import com.mcpscanner.cvetargets.manifest.SeedRequest;
import com.mcpscanner.mcp.ScannerSentinels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArmIdentitiesTest {

    private static final URI ENDPOINT = URI.create("http://127.0.0.1:4100/mcp");
    private static final String IDENTITIES = """
            "identities": {
              "primary": { "name": "alice", "privateMarker": "ALICE_ONLY", "ownedObjects": { "note_id": "a-1" } },
              "others": [ { "id": "bob", "role": "peer", "headers": { "Authorization": "Bearer bob" },
                            "ownedObjects": { "note_id": "b-1" } } ],
              "items": [ { "seed": { "method": "tools/call", "params": { "name": "get_note", "arguments": {} } },
                           "expectation": "owner_only" } ]
            },
            """;
    private static final String PROVISIONED = """
            created users
            {"primary": {"headers": {"Authorization": "Bearer minted-alice"}}, \
            "bob": {"headers": {"Authorization": "Bearer minted-bob"}, "ownedObjects": {"note_id": "b-9"}}}
            """;
    private static final String REPORT = """
            warning printed before the report
            {"listings": {"bob": {"tools": ["get_note"], "resources": []}}, \
            "groundTruth": [{"seed": {}}], "contradictions": %s}
            """;

    @TempDir
    Path root;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String PROVISION_SCRIPT = "provision.py";
    private static final String PROVISION = "\"provision\": \"" + PROVISION_SCRIPT + "\",";

    private final List<List<String>> commands = new ArrayList<>();
    private String contradictions = "[]";
    private int provisionFailures;

    @Test
    void buildsTheRosterFromTheManifestOnceBound() {
        ArmIdentities identities = identities(withIdentities(""), "[]");

        assertThat(identities.get().compared()).isEmpty();
        identities.provision(ENDPOINT, TIMEOUT);
        identities.bind(ENDPOINT);

        PrincipalRoster roster = identities.get();
        assertThat(roster.primary()).satisfies(primary -> {
            assertThat(primary.name()).isEqualTo("alice");
            assertThat(primary.credentialHeaders().get()).containsEntry("Authorization", "Bearer alice");
            assertThat(primary.privateMarker()).contains("ALICE_ONLY");
            assertThat(primary.ownedObjects().all()).containsEntry("note_id", "a-1");
        });
        assertThat(roster.compared()).singleElement().satisfies(bob -> {
            assertThat(bob.role()).isEqualTo(PrincipalRole.PEER);
            assertThat(bob.credentialHeaders().get()).containsEntry("Authorization", "Bearer bob");
            assertThat(bob.ownedObjects().all()).containsEntry("note_id", "b-1");
        });
        assertThat(commands).singleElement().satisfies(command -> assertThat(command)
                .contains("vulnerable", ENDPOINT.toString(), "{\"Authorization\":\"Bearer alice\"}", "{}"));
    }

    @Test
    void provisionsThePrimaryAndOtherIdentitiesBeforeReadinessAndRetriesUntilTheServerAnswers() {
        provisionFailures = 1;
        AuthStrategy primaryAuth = new ProvisionedToken().strategy();
        ArmIdentities identities = new ArmIdentities(withIdentities(PROVISION), "vulnerable", primaryAuth, scripts());

        identities.provision(ENDPOINT, TIMEOUT);
        assertThat(primaryAuth.headers()).containsEntry("Authorization", "Bearer minted-alice");
        identities.bind(ENDPOINT);

        PrincipalRoster roster = identities.get();
        assertThat(roster.primary().credentialHeaders().get()).containsEntry("Authorization", "Bearer minted-alice");
        assertThat(roster.compared()).singleElement().satisfies(bob -> {
            assertThat(bob.credentialHeaders().get()).containsEntry("Authorization", "Bearer minted-bob");
            assertThat(bob.ownedObjects().all()).containsEntry("note_id", "b-9");
        });
        assertThat(commands).hasSize(3);
        assertThat(commands.get(2)).anySatisfy(argument -> assertThat(argument).contains("minted-bob"));
    }

    @Test
    void failsTheArmWhenGroundTruthContradictsTheManifestButKeepsTheEvidence() {
        ArmIdentities identities = identities(withIdentities(""), "[\"bob declared refused, observed same\"]");

        assertThatThrownBy(() -> identities.bind(ENDPOINT))
                .hasMessageContaining("ground truth contradicts the manifest: bob declared refused, observed same");
        assertThat(identities.evidence()).hasValueSatisfying(evidence ->
                assertThat(evidence.groundTruth()).isNotNull());
    }

    @Test
    void servesDeclaredExpectationsAndRecordsEveryOutcome() {
        ArmIdentities identities = identities(withIdentities(""), "[]");
        AccessItemKey declared = new AccessItemKey(AccessItemKey.Kind.TOOL, "get_note");
        AccessItemKey undeclared = new AccessItemKey(AccessItemKey.Kind.RESOURCE, "notes://team");

        identities.bind(ENDPOINT);
        identities.ledger().record(declared, "bob", AccessOutcome.REFUSED);
        identities.ledger().recordAnonymous(declared, AccessOutcome.REFUSED);

        assertThat(identities.ledger().expectationFor(declared)).isEqualTo(AccessExpectation.OWNER_ONLY);
        assertThat(identities.ledger().permitsReplay(declared)).isTrue();
        assertThat(identities.lists("bob", declared)).contains(true);
        assertThat(identities.lists("bob", undeclared)).contains(false);
        assertThat(identities.lists("carol", declared)).isEmpty();
        assertThat(identities.ledger().expectationFor(undeclared)).isEqualTo(AccessExpectation.ANY_SIGNED_IN);
        assertThat(identities.evidence()).hasValueSatisfying(evidence -> assertThat(evidence.accessOutcomes())
                .containsExactly(new AccessRecord(AccessItemKey.Kind.TOOL, "get_note", "bob", AccessOutcome.REFUSED),
                        new AccessRecord(AccessItemKey.Kind.TOOL, "get_note", "anonymous", AccessOutcome.REFUSED)));
    }

    @Test
    void sendsASeedNamingAnIdentityWithOnlyThatIdentitysCredentialsAndTheRoutingSentinel() {
        ArmIdentities identities = identities(withIdentities(""), "[]");
        identities.bind(ENDPOINT);
        SeedRequest asBob = new SeedRequest("tools/call", null, "bob", null);
        SeedRequest asPrimary = new SeedRequest("tools/call", null);
        Map<String, String> session = Map.of("Authorization", "Bearer alice", "Mcp-Session-Id", "alice-session",
                "MCP-Protocol-Version", "2025-11-25");

        List<SeedRequest> routed = identities.routed(List.of(asBob, asPrimary));

        assertThat(routed.get(0).routing().headers(session)).containsOnly(
                Map.entry("Authorization", "Bearer bob"), Map.entry("MCP-Protocol-Version", "2025-11-25"),
                Map.entry(ScannerSentinels.IDENTITY_HEADER, "bob"));
        assertThat(routed.get(1).routing().headers(session)).isEqualTo(session);
    }

    @Test
    void leavesATargetWithoutIdentitiesAlone() {
        ManifestFixtures.writeValidTarget(root, "check-a", "plain");
        ArmIdentities identities = identities(ManifestFixtures.loadOnly(root, "check-a", "plain"), "[]");

        identities.bind(ENDPOINT);

        assertThat(commands).isEmpty();
        assertThat(identities.evidence()).isEmpty();
        assertThat(identities.get().compared()).isEmpty();
        assertThat(identities.get().primary().id()).isEqualTo(Principal.PRIMARY_ID);
    }

    @Test
    void reportsAFailedSetupWithItsOutput() {
        CommandRunner failing = (command, directory, timeout) -> new CommandResult(command, 1, "Traceback: refused");
        ArmIdentities identities = new ArmIdentities(withIdentities(""), "vulnerable",
                new BearerTokenAuthStrategy("alice"), IdentityScripts.using(failing, duration -> { }));

        assertThatThrownBy(() -> identities.bind(ENDPOINT)).hasMessageContaining("exit 1").hasMessageContaining("refused");
        assertThat(identities.evidence()).isEqualTo(Optional.empty());
    }

    private ArmIdentities identities(LoadedTarget target, String contradictions) {
        this.contradictions = contradictions;
        return new ArmIdentities(target, "vulnerable", new BearerTokenAuthStrategy("alice"), scripts());
    }

    private IdentityScripts scripts() {
        CommandRunner runner = (command, directory, timeout) -> {
            commands.add(command);
            if (command.get(0).endsWith(PROVISION_SCRIPT)) {
                return new CommandResult(command, provisionFailures-- > 0 ? 1 : 0, PROVISIONED);
            }
            return new CommandResult(command, 0, REPORT.formatted(contradictions));
        };
        return IdentityScripts.using(runner, duration -> { });
    }

    private LoadedTarget withIdentities(String provision) {
        String manifest = ManifestFixtures.VALID_MANIFEST.formatted("with-identities", "check-a")
                .replace("\"arms\": [", IDENTITIES.replace("\"identities\": {", "\"identities\": {" + provision)
                        + "  \"arms\": [");
        ManifestFixtures.writeTarget(root, "check-a", "with-identities", manifest);
        if (!provision.isEmpty()) {
            write(root.resolve("check-a/with-identities/" + PROVISION_SCRIPT));
        }
        return ManifestFixtures.loadOnly(root, "check-a", "with-identities");
    }

    private static void write(Path file) {
        try {
            Files.writeString(file, "#!/bin/sh\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
