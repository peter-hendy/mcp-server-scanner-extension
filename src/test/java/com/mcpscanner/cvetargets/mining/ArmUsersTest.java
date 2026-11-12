package com.mcpscanner.cvetargets.mining;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.access.mining.MiningReply;
import com.mcpscanner.access.mining.MiningReport;
import com.mcpscanner.access.mining.MiningSender;
import com.mcpscanner.access.mining.MiningStop;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.identity.MinedSource;
import com.mcpscanner.identity.MinedValue;
import com.mcpscanner.identity.ValueScope;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ToolAnnotations;
import com.mcpscanner.scan.ScanInventory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ArmUsersTest {

    private static final Principal ALICE = new Principal(Principal.PRIMARY_ID, "alice", PrincipalRole.PRIMARY,
            Map::of, Optional.of("ALICE_ONLY"), Map.of("doc_id", "d-alice-typed"));
    private static final Principal BOB = new Principal("bob", "bob", PrincipalRole.PEER,
            () -> Map.of("Authorization", "Bearer bob-token"), Optional.of("BOB_ONLY"), Map.of());
    private static final McpToolDefinition LIST_DOCUMENTS = new McpToolDefinition("list_documents", "",
            "{\"type\":\"object\",\"properties\":{}}", List.of(), new ToolAnnotations(null, true, null, null, null));
    private static final McpToolDefinition GET_DOCUMENT = new McpToolDefinition("get_document", "",
            "{\"type\":\"object\",\"properties\":{\"doc_id\":{\"type\":\"string\"}}}", List.of(),
            new ToolAnnotations(null, true, null, null, null));

    private final ArmUsers users = ArmUsers.of(() -> PrincipalRoster.of(ALICE, List.of(BOB)),
            () -> new BearerTokenAuthStrategy("alice-token"));

    @Test
    void theRegistryRosterKeepsTheArmsIdsNamesRolesCredentialsMarkersAndEnteredValues() {
        PrincipalRoster roster = users.roster().get();

        assertThat(roster.primary().id()).isEqualTo(Principal.PRIMARY_ID);
        assertThat(roster.primary().name()).isEqualTo("alice");
        assertThat(roster.primary().credentialHeaders().get()).containsEntry("Authorization", "Bearer alice-token");
        assertThat(roster.primary().privateMarker()).contains("ALICE_ONLY");
        assertThat(roster.primary().ownedObjects().all()).containsExactlyEntriesOf(Map.of("doc_id", "d-alice-typed"));
        assertThat(roster.compared()).singleElement().satisfies(bob -> {
            assertThat(bob.id()).isEqualTo("bob");
            assertThat(bob.role()).isEqualTo(PrincipalRole.PEER);
            assertThat(bob.credentialHeaders().get()).containsEntry("Authorization", "Bearer bob-token");
            assertThat(bob.privateMarker()).contains("BOB_ONLY");
        });
    }

    @Test
    void miningThroughAccessMiningStoresPrivateValuesThatEnteredValuesStillOverride() {
        MiningSender replies = (identity, call) -> MiningReply.of(200, documentsFor(identity.name()));

        MiningReport report = users.mine(new ScanInventory(List.of(LIST_DOCUMENTS, GET_DOCUMENT), List.of(), List.of(), List.of()),
                replies);

        assertThat(report.stop()).isEqualTo(MiningStop.COMPLETED);
        PrincipalRoster roster = users.roster().get();
        assertThat(roster.compared().get(0).ownedObjects().all()).containsEntry("doc_id", "d-bob-0042");
        assertThat(roster.primary().ownedObjects().all()).containsEntry("doc_id", "d-alice-typed");
    }

    @Test
    void minedValuesReachTheRosterOnlyThroughTheOwnedValueStore() {
        users.ownedValues().replaceMined("bob", List.of(new MinedValue("doc_id", "d-bob-1",
                new MinedSource("tools/call list_documents", "documents[0].doc_id"), ValueScope.PRIVATE)));

        assertThat(users.roster().get().compared().get(0).ownedObjects().all()).containsEntry("doc_id", "d-bob-1");
    }

    private static ObjectNode documentsFor(String user) {
        ArrayNode documents = McpObjectMapper.INSTANCE.createArrayNode();
        Map.of("alice", "d-alice-0001", "bob", "d-bob-0042").entrySet().stream()
                .filter(owned -> owned.getKey().equals(user))
                .forEach(owned -> documents.add(McpObjectMapper.INSTANCE.createObjectNode().put("doc_id", owned.getValue())));
        ObjectNode result = McpObjectMapper.INSTANCE.createObjectNode();
        result.set("structuredContent", McpObjectMapper.INSTANCE.createObjectNode().set("documents", documents));
        return result;
    }
}
