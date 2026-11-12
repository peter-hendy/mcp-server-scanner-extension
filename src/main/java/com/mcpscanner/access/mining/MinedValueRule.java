package com.mcpscanner.access.mining;

import com.mcpscanner.checks.api.OwnedObjects;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.OwnerArgumentSource;

import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class MinedValueRule {

    public static final MinedValueRule READ_TOOLS_ONLY = new MinedValueRule(() -> false);

    private final BooleanSupplier inWriteTools;

    public MinedValueRule(BooleanSupplier inWriteTools) {
        this.inWriteTools = inWriteTools;
    }

    public static MinedValueRule of(MiningPreferences preferences) {
        return new MinedValueRule(preferences::minedIdsInWriteTools);
    }

    public boolean takesMinedValues(McpToolDefinition tool) {
        return ToolGate.takesMinedValues(tool) || inWriteTools.getAsBoolean();
    }

    public OwnerArgumentSource argumentsOf(Supplier<OwnedObjects> owned) {
        return new OwnerArgumentSource() {
            @Override
            public Map<String, String> forTool(McpToolDefinition tool) {
                return owned.get().forTool(takesMinedValues(tool));
            }

            @Override
            public Set<String> minedArgumentsFor(McpToolDefinition tool) {
                return owned.get().minedArguments(takesMinedValues(tool));
            }
        };
    }
}
