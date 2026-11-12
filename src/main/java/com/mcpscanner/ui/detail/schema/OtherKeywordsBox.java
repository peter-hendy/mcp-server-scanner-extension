package com.mcpscanner.ui.detail.schema;

import com.mcpscanner.ui.widgets.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;

final class OtherKeywordsBox extends JPanel {

    private static final String SEPARATOR = "   ·   ";
    private static final int GAP = 6;
    private static final int PADDING = 10;

    OtherKeywordsBox(List<String> keywords) {
        super(new BorderLayout(0, GAP));
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeColors.separator()),
                BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING)));
        add(SchemaLabels.strong("Other schema keywords"), BorderLayout.NORTH);
        add(SchemaLabels.wrappingCode(String.join(SEPARATOR, keywords)), BorderLayout.CENTER);
    }
}
