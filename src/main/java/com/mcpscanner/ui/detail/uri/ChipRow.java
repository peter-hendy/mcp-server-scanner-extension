package com.mcpscanner.ui.detail.uri;

import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.widgets.WrapLayout;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;

final class ChipRow extends JPanel {

    private static final int GAP = 6;

    private ChipRow(List<String> texts) {
        super(new WrapLayout(FlowLayout.LEFT, GAP, 0));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        texts.forEach(text -> add(Chip.neutral(text)));
    }

    static ChipRow of(List<String> texts) {
        return new ChipRow(texts);
    }
}
