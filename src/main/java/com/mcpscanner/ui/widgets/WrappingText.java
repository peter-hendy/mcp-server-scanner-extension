package com.mcpscanner.ui.widgets;

import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class WrappingText extends JTextArea {

    private static final int NARROW_COLUMNS = 1;

    public WrappingText(String text) {
        super(text);
        setEditable(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        setOpaque(false);
        setColumns(NARROW_COLUMNS);
        setBorder(BorderFactory.createEmptyBorder());
        setFont(UIManager.getFont("Label.font"));
    }

    public WrappingText muted() {
        setForeground(ThemeColors.muted());
        return this;
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        boolean widthChanged = width != getWidth();
        super.setBounds(x, y, width, height);
        if (widthChanged) {
            SwingUtilities.invokeLater(this::revalidate);
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setFont(UIManager.getFont("Label.font"));
    }
}
