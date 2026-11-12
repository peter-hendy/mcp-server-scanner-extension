package com.mcpscanner.ui.serverinfo;

import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.detail.DetailLabels;
import com.mcpscanner.ui.widgets.UiStyle;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

public final class ProtocolVersionView {

    private static final String LATEST = "latest";
    private static final int CHIP_GAP = 8;

    private ProtocolVersionView() {
    }

    public static JComponent of(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.add(UiStyle.monospaced(DetailLabels.literal(version)));
        if (SupportedProtocolVersions.isLatest(version)) {
            row.add(Box.createHorizontalStrut(CHIP_GAP));
            row.add(Chip.positive(LATEST));
        }
        row.add(Box.createHorizontalGlue());
        return row;
    }
}
