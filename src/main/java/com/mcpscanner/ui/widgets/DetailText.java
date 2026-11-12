package com.mcpscanner.ui.widgets;

import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public final class DetailText extends JScrollPane {

    private final JTextArea area;

    private DetailText(JTextArea area) {
        super(area);
        this.area = area;
        area.setEditable(false);
        setVisible(false);
    }

    public static DetailText prose() {
        JTextArea area = new JTextArea();
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return new DetailText(area);
    }

    public static DetailText code() {
        return new DetailText(UiStyle.monospaced(new JTextArea()));
    }

    public void show(String text) {
        String content = text == null ? "" : text;
        area.setText(content);
        area.setCaretPosition(0);
        setVisible(!content.isBlank());
    }

    public boolean hasContent() {
        return isVisible();
    }

    public JTextArea area() {
        return area;
    }
}
