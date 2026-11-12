package com.mcpscanner.ui.widgets;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import java.awt.Component;

public final class DetailLine extends JLabel {

    private static final int GAP = 4;

    public DetailLine() {
        SwingHtmlGuard.disableHtml(this);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(0, 0, GAP, 0));
        setVisible(false);
    }

    public static DetailLine monospaced() {
        return UiStyle.monospaced(new DetailLine());
    }

    public void show(String text) {
        String content = text == null ? "" : text;
        setText(content);
        setVisible(!content.isBlank());
    }
}
