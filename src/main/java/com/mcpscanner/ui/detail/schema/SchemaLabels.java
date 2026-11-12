package com.mcpscanner.ui.detail.schema;

import com.mcpscanner.ui.widgets.Contrast;
import com.mcpscanner.ui.widgets.Placeholders;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;
import com.mcpscanner.ui.widgets.ThemeColors;
import com.mcpscanner.ui.widgets.UiStyle;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

final class SchemaLabels {

    private static final double CODE_BOX_SHARE = 0.05;

    private SchemaLabels() {
    }

    static JLabel text(String text) {
        return SwingHtmlGuard.disableHtml(new JLabel(text));
    }

    static JLabel strong(String text) {
        JLabel label = text(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    static JLabel muted(String text) {
        JLabel label = text(text);
        label.setForeground(ThemeColors.muted());
        return label;
    }

    static JLabel code(String text) {
        return UiStyle.monospaced(text(text));
    }

    static JLabel strongCode(String text) {
        JLabel label = code(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    static WrappingText prose(String text) {
        return SwingHtmlGuard.disableHtml(new WrappingText(text));
    }

    static WrappingText placeholder(String text) {
        return SwingHtmlGuard.disableHtml(Placeholders.wrapping(text));
    }

    static WrappingText wrappingCode(String text) {
        return UiStyle.monospaced(prose(text));
    }

    static WrappingText codeBox(String text) {
        WrappingText box = wrappingCode(text);
        box.setWrapStyleWord(false);
        box.setOpaque(true);
        box.setBackground(codeBoxBackground());
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeColors.separator()),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        return box;
    }

    private static Color codeBoxBackground() {
        return Contrast.blend(themed("Panel.background", Color.WHITE), themed("Label.foreground", Color.BLACK),
                CODE_BOX_SHARE);
    }

    private static Color themed(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }
}
