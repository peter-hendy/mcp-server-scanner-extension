package com.mcpscanner.access.mining;

import com.mcpscanner.checks.api.OwnedObjects;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.OwnerArgumentSource;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.mcpscanner.access.mining.ScriptedServer.readOnlyTool;
import static com.mcpscanner.access.mining.ScriptedServer.stringArgument;
import static com.mcpscanner.access.mining.ScriptedServer.unannotatedTool;
import static org.assertj.core.api.Assertions.assertThat;

class MinedValueRuleTest {

    private static final McpToolDefinition GET_INVOICE = readOnlyTool("get_invoice", stringArgument("invoice_id"));
    private static final McpToolDefinition DELETE_INVOICE = unannotatedTool("delete_invoice",
            stringArgument("invoice_id"));
    private static final OwnedObjects OWNED = new OwnedObjects(Map.of("note_id", "n-typed"),
            Map.of("invoice_id", "inv-mined", "note_id", "n-mined"));

    private final AtomicBoolean inWriteTools = new AtomicBoolean();
    private final OwnerArgumentSource arguments = new MinedValueRule(inWriteTools::get).argumentsOf(() -> OWNED);

    @Test
    void offAWriteToolGetsEnteredValuesOnly() {
        assertThat(arguments.forTool(DELETE_INVOICE)).containsExactly(Map.entry("note_id", "n-typed"));
        assertThat(arguments.minedArgumentsFor(DELETE_INVOICE)).isEmpty();
    }

    @Test
    void onAWriteToolAlsoGetsMinedValuesBehindEnteredOnes() {
        inWriteTools.set(true);

        assertThat(arguments.forTool(DELETE_INVOICE))
                .containsExactly(Map.entry("note_id", "n-typed"), Map.entry("invoice_id", "inv-mined"));
        assertThat(arguments.minedArgumentsFor(DELETE_INVOICE)).containsExactly("invoice_id");
    }

    @Test
    void aReadToolGetsMinedValuesEitherWay() {
        assertThat(arguments.forTool(GET_INVOICE)).containsEntry("invoice_id", "inv-mined");
        assertThat(arguments.minedArgumentsFor(GET_INVOICE)).containsExactly("invoice_id");
    }
}
