package com.mcpscanner.ui.detail;

import com.mcpscanner.ui.widgets.UiStyle;

import javax.swing.JTextPane;
import javax.swing.JViewport;
import java.awt.Container;

final class JsonTextPane extends JTextPane {

    private boolean wrapping = true;

    JsonTextPane() {
        setEditable(false);
        UiStyle.monospaced(this);
    }

    void display(String text) {
        JsonDocumentBuilder builder = new JsonDocumentBuilder(new JsonPalette(getBackground()), getFont());
        setStyledDocument(builder.build(text));
        setCaretPosition(0);
    }

    void setWrapping(boolean wrapping) {
        this.wrapping = wrapping;
        revalidate();
        repaint();
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return wrapping || fitsViewport();
    }

    private boolean fitsViewport() {
        Container parent = getParent();
        return parent instanceof JViewport && getUI().getPreferredSize(this).width <= parent.getWidth();
    }
}
