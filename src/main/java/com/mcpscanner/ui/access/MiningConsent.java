package com.mcpscanner.ui.access;

import com.mcpscanner.access.mining.AccessMining;
import com.mcpscanner.access.mining.ToolGate;
import com.mcpscanner.mcp.McpToolDefinition;

import java.awt.Component;
import java.util.List;
import java.util.OptionalLong;

final class MiningConsent {

    private final AccessMining mining;
    private final MiningDialogs dialogs;
    private OptionalLong confirmedConnection = OptionalLong.empty();

    MiningConsent(AccessMining mining, MiningDialogs dialogs) {
        this.mining = mining;
        this.dialogs = dialogs;
    }

    boolean confirmMining(Component parent) {
        if (confirmed() || dialogs.confirmMining(parent, toolsToCall())) {
            record();
            return true;
        }
        return false;
    }

    MineFirstChoice mineFirst(Component parent) {
        MineFirstChoice choice = dialogs.mineFirst(parent, toolsToCall());
        if (choice == MineFirstChoice.MINE) {
            record();
        }
        return choice;
    }

    List<String> toolsToCall() {
        List<McpToolDefinition> tools = mining.tools();
        ToolGate gate = new ToolGate(mining.preferences().settings(tools).allowedTools());
        return tools.stream().filter(gate::mayCall).map(McpToolDefinition::name).toList();
    }

    private boolean confirmed() {
        return confirmedConnection.equals(OptionalLong.of(mining.connection()));
    }

    private void record() {
        confirmedConnection = OptionalLong.of(mining.connection());
    }
}
