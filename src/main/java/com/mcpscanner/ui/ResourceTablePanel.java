package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.mcp.McpResourceDefinition;

import java.util.List;

public class ResourceTablePanel
        extends AbstractInventoryTablePanel<McpResourceDefinition, ResourceDetailPanel> {

    @Override
    protected SelectableInventoryTableModel<McpResourceDefinition> createTableModel() {
        return new ResourceTableModel();
    }

    @Override
    protected String filterSubject() {
        return "name or URI";
    }

    @Override
    protected String itemNoun() {
        return "resource";
    }

    @Override
    protected String listMethod() {
        return McpMethods.RESOURCES_LIST;
    }

    @Override
    protected ResourceDetailPanel createDetailPanel() {
        return new ResourceDetailPanel(new DetailSubject<>(itemNoun(), McpResourceDefinition::name,
                McpResourceDefinition::description, McpResourceDefinition::raw, listMethod()));
    }

    public List<McpResourceDefinition> selectedResources() {
        return getTableModel().selectedItems();
    }
}
