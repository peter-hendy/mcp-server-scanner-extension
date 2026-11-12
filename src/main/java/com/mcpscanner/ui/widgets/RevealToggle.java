package com.mcpscanner.ui.widgets;

import javax.swing.JToggleButton;
import java.awt.Dimension;

public final class RevealToggle extends JToggleButton {

    private final String noun;

    public RevealToggle(String noun) {
        super(EyeIcon.open());
        this.noun = noun;
        setSelectedIcon(EyeIcon.slashed());
        addItemListener(event -> describe());
        describe();
    }

    public void matchHeight(int side) {
        setPreferredSize(new Dimension(side, side));
    }

    private void describe() {
        String action = (isSelected() ? "Hide " : "Show ") + noun;
        setToolTipText(action);
        getAccessibleContext().setAccessibleName(action);
    }
}
