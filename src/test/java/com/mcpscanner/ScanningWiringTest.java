package com.mcpscanner;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.PersistedObject;
import com.mcpscanner.access.MatrixAccessLedger;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.registry.CheckInstrumentation;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.identity.EnteredValue;
import com.mcpscanner.identity.MinedSource;
import com.mcpscanner.identity.MinedValue;
import com.mcpscanner.identity.OwnedValues;
import com.mcpscanner.identity.ValueScope;
import com.mcpscanner.identity.discovery.IdentityListings;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.ScanMode;
import com.mcpscanner.scan.ScanModeSetting;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScanningWiringTest {

    private static final com.mcpscanner.mcp.McpToolDefinition READ_TOOL = new com.mcpscanner.mcp.McpToolDefinition(
            "get_document", "", "{}", List.of(), new com.mcpscanner.mcp.ToolAnnotations(null, true, null, null, null));

    private final MontoyaApi api = mock(MontoyaApi.class, RETURNS_DEEP_STUBS);
    private ExtensionFoundation foundation;
    private AccessWiring access;
    private ScanningWiring scanning;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        PersistedObject savedSettings = InMemoryPersistedObject.create();
        when(api.persistence().extensionData()).thenReturn(savedSettings);
        foundation = ExtensionFoundation.create(api);
        ConnectionWiring connection = ConnectionWiring.create(api, foundation);
        access = AccessWiring.create(foundation, connection, new IdentityListings());
        ScanFieldWiring fields = ScanFieldWiring.create(api, foundation);
        scanning = ScanningWiring.create(api, foundation, connection, access, fields,
                EngineWiring.create(api, foundation, connection.burpHttp(), fields.everyField()),
                CheckInstrumentation.NONE, java.util.function.UnaryOperator.identity());
    }

    @Test
    void scanningSharesTheFoundationIdentityRegistry() {
        assertThat(scanning.identities()).isSameAs(foundation.identities());
    }

    @Test
    void scanModeIsPersistedInTheFoundationConfigStore() {
        scanning.scanModeSetting().select(ScanMode.MCP_AND_BURP);

        assertThat(new ScanModeSetting(foundation.configStore()).current())
                .isEqualTo(ScanMode.MCP_AND_BURP);
    }

    @Test
    void scanFillsInTheOwnedObjectsOfTheUserItConnectsAs() {
        IdentityRegistry identities = foundation.identities();
        identities.update(identities.primaryUser().withOwnedValues(OwnedValues.entered(Map.of("doc_id", "d-1"))));

        assertThat(scanning.scanOwnerArguments().forTool(READ_TOOL)).containsExactly(Map.entry("doc_id", "d-1"));
    }

    @Test
    void scanFillsInPrivateMinedValuesBehindEnteredOnes() {
        IdentityRegistry identities = foundation.identities();
        MinedSource source = new MinedSource("tools/call list_documents", "items[0].doc_id");
        identities.update(identities.primaryUser().withOwnedValues(OwnedValues.of(List.of(
                new MinedValue("doc_id", "d-mined", source, ValueScope.PRIVATE),
                new MinedValue("note_id", "n-mined", source, ValueScope.PRIVATE),
                new EnteredValue("doc_id", "d-typed")))));

        assertThat(scanning.scanOwnerArguments().forTool(READ_TOOL))
                .containsExactly(Map.entry("doc_id", "d-typed"), Map.entry("note_id", "n-mined"));
    }

    @Test
    void scanFollowsConnectAsToTheNewUsersOwnedObjects() {
        IdentityRegistry identities = foundation.identities();
        identities.update(identities.primaryUser()
                .withOwnedValues(OwnedValues.entered(Map.of("doc_id", "d-alice"))));
        Identity bob = identities.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER)
                .withOwnedValues(OwnedValues.entered(Map.of("doc_id", "d-bob"))));
        com.mcpscanner.scan.OwnerArgumentSource owned = scanning.scanOwnerArguments();

        identities.makePrimary(bob.id());

        assertThat(owned.forTool(READ_TOOL)).containsExactly(Map.entry("doc_id", "d-bob"));
    }

    @Test
    void sessionResetKeepsLiveIdentityCredentials() {
        IdentityRegistry identities = foundation.identities();
        Identity bob = identities.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        identities.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-token"));

        scanning.sessionResets().forEach(Runnable::run);

        assertThat(identities.credential(bob.id())).isPresent();
        assertThat(identities.otherUsers()).containsExactly(bob);
    }

    @Test
    void checksShareTheMatrixBackedLedgerAndCrossIdentityAccessIsNoLongerAScanCheck() {
        assertThat(access.ledger()).isInstanceOf(MatrixAccessLedger.class);
        assertThat(scanning.catalog().find("crossidentityaccess")).isEmpty();
    }

    @Test
    void accessSessionResetWithdrawsConfirmedWriteReplays() {
        access.confirmations().confirm(List.of(new McpToolDefinition("delete", "", "{}")));

        access.sessionResets().forEach(Runnable::run);

        assertThat(access.confirmations().confirmed("delete")).isFalse();
        assertThat(access.ledger().permitsReplay(new AccessItemKey(AccessItemKey.Kind.TOOL, "delete"))).isFalse();
    }
}
