package com.mcpscanner.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.detail.DetailSection;
import com.mcpscanner.ui.detail.IconStrip;
import com.mcpscanner.ui.detail.McpAppsSection;
import com.mcpscanner.ui.detail.MetaSection;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

final class ToolDetailsTab extends JPanel {

    private static final int SECTION_GAP = 12;

    private final DetailSection icons = new DetailSection("Icons");
    private final DetailSection mcpApps = new DetailSection(McpAppsSection.TITLE);
    private final DetailSection meta = new DetailSection(MetaSection.TITLE);

    ToolDetailsTab() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        add(icons);
        add(Box.createVerticalStrut(SECTION_GAP));
        add(mcpApps);
        add(Box.createVerticalStrut(SECTION_GAP));
        add(meta);
    }

    void show(JsonNode raw) {
        icons.show(IconStrip.of(raw.path("icons")));
        mcpApps.show(McpAppsSection.grid(raw.path("_meta")));
        meta.show(MetaSection.grid(raw.path("_meta")));
    }

    boolean hasContent() {
        return icons.hasContent() || mcpApps.hasContent() || meta.hasContent();
    }
}
