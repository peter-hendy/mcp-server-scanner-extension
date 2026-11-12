package com.mcpscanner.ui.widgets;

import javax.swing.JTable;
import javax.swing.UIManager;
import java.awt.Color;

public final class SteadySelectionColours {

    private SteadySelectionColours() {
    }

    public static JTable install(JTable table) {
        pin(table);
        table.addPropertyChangeListener("UI", event -> pin(table));
        return table;
    }

    private static void pin(JTable table) {
        table.setSelectionBackground(plain(UIManager.getColor("Table.selectionBackground"),
                table.getSelectionBackground()));
        table.setSelectionForeground(plain(UIManager.getColor("Table.selectionForeground"),
                table.getSelectionForeground()));
    }

    private static Color plain(Color themed, Color fallback) {
        Color source = themed != null ? themed : fallback;
        return source == null ? null : new Color(source.getRGB(), true);
    }
}
