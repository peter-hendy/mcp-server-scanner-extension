package com.mcpscanner.ui.widgets;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.util.function.Consumer;

public final class FadedWhenDisabled {

    public static final String FLATLAF_STYLE = "FlatLaf.style";
    private static final double FADE = 0.55;

    private FadedWhenDisabled() {
    }

    public static <T extends JTextComponent> T install(T field) {
        return onEveryTheme(field, faded -> field.setDisabledTextColor(faded));
    }

    public static <T extends JComboBox<?>> T install(T combo) {
        return onEveryTheme(combo, faded -> combo.putClientProperty(FLATLAF_STYLE, "disabledForeground: " + hex(faded)));
    }

    public static <T extends AbstractButton> T install(T button) {
        return onEveryTheme(button, faded -> button.putClientProperty(FLATLAF_STYLE, "disabledText: " + hex(faded)));
    }

    private static Color fadedColourOf(JComponent component) {
        return Contrast.blend(component.getForeground(), component.getBackground(), FADE);
    }

    private static <T extends JComponent> T onEveryTheme(T component, Consumer<Color> applyFaded) {
        applyFaded.accept(fadedColourOf(component));
        component.addPropertyChangeListener("UI", event -> applyFaded.accept(fadedColourOf(component)));
        return component;
    }

    private static String hex(Color colour) {
        return String.format("#%02x%02x%02x", colour.getRed(), colour.getGreen(), colour.getBlue());
    }
}
