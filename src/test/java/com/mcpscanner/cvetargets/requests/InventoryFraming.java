package com.mcpscanner.cvetargets.requests;

import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.mcp.McpOperationFraming;
import com.mcpscanner.mcp.ParamHeaderFramer;
import com.mcpscanner.mcp.ResendableRequests;
import com.mcpscanner.mcp.ToolParamHeaders;

final class InventoryFraming {

    private InventoryFraming() {}

    static McpOperationFraming.Framer withParamHeaders(McpOperationFraming.Framer sessionFramer,
                                                       ConnectResult inventory) {
        ToolParamHeaders tools = new ToolParamHeaders(System.err::println);
        tools.learn(inventory.tools());
        return new ParamHeaderFramer(sessionFramer, tools);
    }

    static ResendableRequests resendable(ConnectResult inventory) {
        ResendableRequests resendable = new ResendableRequests();
        resendable.learn(inventory.tools());
        return resendable;
    }
}
