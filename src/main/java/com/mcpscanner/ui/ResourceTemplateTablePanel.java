package com.mcpscanner.ui;

import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;

import java.util.List;

public class ResourceTemplateTablePanel extends AbstractInventoryTablePanel<McpResourceTemplateDefinition,
        ResourceTemplateDetailPanel> {

    @Override
    protected SelectableInventoryTableModel<McpResourceTemplateDefinition> createTableModel() {
        return new ResourceTemplateTableModel();
    }

    @Override
    protected String filterSubject() {
        return "name or URI";
    }

    @Override
    protected String itemNoun() {
        return "template";
    }

    @Override
    protected String listMethod() {
        return McpMethods.RESOURCE_TEMPLATES_LIST;
    }

    @Override
    protected ResourceTemplateDetailPanel createDetailPanel() {
        return new ResourceTemplateDetailPanel(new DetailSubject<>(itemNoun(), McpResourceTemplateDefinition::name,
                McpResourceTemplateDefinition::description, McpResourceTemplateDefinition::raw, listMethod()));
    }

    public List<McpResourceTemplateDefinition> selectedResourceTemplates() {
        return getTableModel().selectedItems();
    }
}
