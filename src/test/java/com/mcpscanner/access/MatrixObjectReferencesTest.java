package com.mcpscanner.access;

import com.mcpscanner.access.mining.MinedValueRule;
import com.mcpscanner.checks.api.AccessItemKey;
import com.mcpscanner.checks.api.OwnedObjects;
import com.mcpscanner.checks.api.Principal;
import com.mcpscanner.checks.api.PrincipalRole;
import com.mcpscanner.mcp.McpResourceDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mcpscanner.access.AccessFixtures.ALICE;
import static com.mcpscanner.access.AccessFixtures.readOnlyTool;
import static com.mcpscanner.access.AccessFixtures.tool;
import static org.assertj.core.api.Assertions.assertThat;

class MatrixObjectReferencesTest {

    private static final Principal BOB = new Principal("bob", "bob", PrincipalRole.PEER, Map::of, Optional.empty(),
            new OwnedObjects(Map.of("note_id", "n-typed"), Map.of("invoice_id", "inv-bob")), Map::of);
    private static final McpResourceDefinition NOTES = new McpResourceDefinition("notes://bob", "notes", "",
            "text/plain", List.of());

    private final AtomicBoolean minedInWriteTools = new AtomicBoolean();
    private final MatrixObjectReferences references = new MatrixObjectReferences(
            new AccessFixtures().compare(BOB).lists(ALICE, List.of(tool("delete_invoice"),
                    readOnlyTool("get_invoice")), List.of(NOTES)).matrix(),
            new MinedValueRule(minedInWriteTools::get));

    @Test
    void offAWriteToolGetsEnteredValuesOnly() {
        assertThat(references.of(BOB, toolKey("delete_invoice"))).containsExactly(Map.entry("note_id", "n-typed"));
    }

    @Test
    void onAWriteToolGetsMinedValuesToo() {
        minedInWriteTools.set(true);

        assertThat(references.of(BOB, toolKey("delete_invoice"))).containsEntry("invoice_id", "inv-bob");
    }

    @Test
    void readToolsAndResourcesGetMinedValuesEitherWay() {
        assertThat(references.of(BOB, toolKey("get_invoice"))).containsEntry("invoice_id", "inv-bob");
        assertThat(references.of(BOB, new AccessItemKey(AccessItemKey.Kind.RESOURCE, "notes://bob")))
                .containsEntry("invoice_id", "inv-bob");
    }

    @Test
    void anItemOutsideTheMatrixGetsEnteredValuesOnly() {
        assertThat(references.of(BOB, toolKey("unknown"))).containsExactly(Map.entry("note_id", "n-typed"));
    }

    private static AccessItemKey toolKey(String name) {
        return new AccessItemKey(AccessItemKey.Kind.TOOL, name);
    }
}
