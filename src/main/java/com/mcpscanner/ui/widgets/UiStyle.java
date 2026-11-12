package com.mcpscanner.ui.widgets;

import javax.swing.JComponent;
import java.awt.Font;

public final class UiStyle {

    public static final String STYLE_CLASS = "FlatLaf.styleClass";
    public static final String PRIMARY = "primary";
    public static final String MONOSPACED = "monospaced";
    public static final String SMALL = "small";
    public static final String SEMIBOLD = "semibold";
    public static final String HEADING = "h4";

    private UiStyle() {
    }

    public static <T extends JComponent> T styleClass(T component, String styleClass) {
        component.putClientProperty(STYLE_CLASS, styleClass);
        return component;
    }

    public static <T extends JComponent> T monospaced(T component) {
        Font base = component.getFont();
        if (base != null) {
            component.setFont(new Font(Font.MONOSPACED, Font.PLAIN, base.getSize()));
        }
        return styleClass(component, MONOSPACED);
    }
}
