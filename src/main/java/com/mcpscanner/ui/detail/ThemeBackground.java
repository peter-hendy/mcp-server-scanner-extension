package com.mcpscanner.ui.detail;

import javax.swing.UIManager;
import java.awt.Color;

final class ThemeBackground {

    private ThemeBackground() {
    }

    static Color panel() {
        Color background = UIManager.getColor("Panel.background");
        return background == null ? Color.WHITE : background;
    }
}
