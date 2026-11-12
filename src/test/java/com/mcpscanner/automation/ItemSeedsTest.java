package com.mcpscanner.automation;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.access.AccessCell;
import com.mcpscanner.access.AccessFixtures;
import com.mcpscanner.access.AccessItem;
import com.mcpscanner.access.AccessRow;
import com.mcpscanner.access.IdentityRequests;
import com.mcpscanner.access.ListingState;
import com.mcpscanner.access.OwnerRequests;
import com.mcpscanner.access.mining.MinedValueRule;
import com.mcpscanner.checks.api.AccessExpectation;
import com.mcpscanner.checks.api.OwnedObjects;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.JsonSchemaDefaults;
import com.mcpscanner.scan.ScanTarget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ItemSeedsTest {

    private static final String ADMIN_EFFECT_SCHEMA = """
            {"type":"object","properties":{"sql":{"type":"string"},"limit":{"type":"integer"}},"required":["sql"]}""";
    private static final McpToolDefinition ADMIN_EFFECT = new McpToolDefinition("admin-effect", "", ADMIN_EFFECT_SCHEMA);
    private static final McpToolDefinition SEARCH = new McpToolDefinition("search", "", ADMIN_EFFECT_SCHEMA);

    private final ItemSeeds seeds = new ItemSeeds();
    private final List<ScanTarget> rendered = new ArrayList<>();

    @Test
    void aSeededToolRendersExactlyTheSeedsStringArgument() {
        ObjectNode arguments = arguments("{\"sql\":\"INSERT INTO privileged_effect(marker) VALUES ('X') RETURNING marker\"}");

        seeds.seedWith(List.of(toolCall("admin-effect", arguments)));

        assertThat(renderedArguments(seeds.pinned(new ScanTarget.Tool(ADMIN_EFFECT)))).isEqualTo(arguments);
    }

    @Test
    void aNonStringSeedArgumentIsPinnedAsTheSameJsonValue() {
        ObjectNode arguments = arguments("{\"sql\":\"select 1\",\"limit\":7}");

        seeds.seedWith(List.of(toolCall("admin-effect", arguments)));

        assertThat(renderedArguments(seeds.pinned(new ScanTarget.Tool(ADMIN_EFFECT)))).isEqualTo(arguments);
    }

    @Test
    void aSeedArgumentTheSchemaDoesNotDeclareIsStillSent() {
        ObjectNode arguments = arguments("{\"sql\":\"select 1\",\"options\":{\"dry\":false}}");

        seeds.seedWith(List.of(toolCall("admin-effect", arguments)));

        assertThat(renderedArguments(seeds.pinned(new ScanTarget.Tool(ADMIN_EFFECT)))).isEqualTo(arguments);
    }

    @Test
    void leavesAnUnseededToolAndAResourceUntouched() {
        ScanTarget search = new ScanTarget.Tool(SEARCH);
        ScanTarget notes = new ScanTarget.Resource(AccessFixtures.resource("notes://team"));

        seeds.seedWith(List.of(toolCall("admin-effect", arguments("{\"sql\":\"select 1\"}")),
                new SeedCall("resources/read", arguments("{\"uri\":\"notes://team\"}"))));

        assertThat(seeds.pinned(search)).isEqualTo(search);
        assertThat(seeds.pinned(notes)).isEqualTo(notes);
    }

    @Test
    void aLaterCommandWithoutSeedsPinsNothing() {
        ScanTarget adminEffect = new ScanTarget.Tool(ADMIN_EFFECT);
        seeds.seedWith(List.of(toolCall("admin-effect", arguments("{\"sql\":\"select 1\"}"))));

        seeds.seedWith(List.of());

        assertThat(seeds.pinned(adminEffect)).isEqualTo(adminEffect);
    }

    @Test
    void theSeedWinsOverTheOwnersOwnedValuesInTheRenderedOwnerRequest() {
        Principal owner = new Principal(Principal.PRIMARY_ID, "alice", PrincipalRole.PRIMARY, Map::of,
                Optional.empty(), new OwnedObjects(Map.of("sql", "doc-alice-7", "limit", "9"), Map.of()), Map::of);
        ObjectNode arguments = arguments("{\"sql\":\"doc-public-1\",\"limit\":3}");
        seeds.seedWith(List.of(toolCall("admin-effect", arguments)));

        ownerRequestsPinnedBySeeds().forOwner(new AccessRow(AccessItem.tool(ADMIN_EFFECT), owner,
                AccessExpectation.ANY_SIGNED_IN, true, Map.of(),
                AccessCell.of(ListingState.NO_LISTING, Optional.empty(), false)));

        assertThat(renderedArguments(rendered.get(0))).isEqualTo(arguments);
    }

    private OwnerRequests ownerRequestsPinnedBySeeds() {
        return new OwnerRequests(target -> {
            rendered.add(seeds.pinned(target));
            return mock(HttpRequest.class);
        }, mock(IdentityRequests.class), MinedValueRule.READ_TOOLS_ONLY);
    }

    private static ObjectNode renderedArguments(ScanTarget target) {
        return JsonSchemaDefaults.defaultArguments(((ScanTarget.Tool) target).tool().inputSchema());
    }

    private static SeedCall toolCall(String name, ObjectNode arguments) {
        ObjectNode params = McpObjectMapper.INSTANCE.createObjectNode().put("name", name);
        params.set("arguments", arguments);
        return new SeedCall("tools/call", params);
    }

    private static ObjectNode arguments(String json) {
        try {
            return (ObjectNode) McpObjectMapper.INSTANCE.readTree(json);
        } catch (Exception unreadable) {
            throw new IllegalArgumentException(unreadable);
        }
    }
}
