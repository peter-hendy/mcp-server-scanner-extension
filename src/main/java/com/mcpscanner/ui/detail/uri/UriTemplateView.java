package com.mcpscanner.ui.detail.uri;

import com.mcpscanner.mcp.uritemplate.TemplateExpression;
import com.mcpscanner.mcp.uritemplate.UriTemplate;
import com.mcpscanner.ui.widgets.Contrast;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;
import com.mcpscanner.ui.widgets.ThemeColors;
import com.mcpscanner.ui.widgets.UiStyle;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Component;

final class UriTemplateView {

    private static final double HIGHLIGHT_SHARE = 0.7;

    private UriTemplateView() {
    }

    static JComponent highlighted(UriTemplate template) {
        JTextPane pane = UiStyle.monospaced(new JTextPane());
        pane.setEditable(false);
        pane.setOpaque(false);
        pane.setText(template.text());
        template.expressions().forEach(expression -> highlight(pane.getStyledDocument(), expression));
        pane.setAlignmentX(Component.LEFT_ALIGNMENT);
        return pane;
    }

    static JComponent unreadable(String text, String warning) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        WrappingText template = UiStyle.monospaced(new WrappingText(text));
        template.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(template);
        box.add(warningLabel(warning));
        return box;
    }

    private static void highlight(StyledDocument document, TemplateExpression expression) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        StyleConstants.setBold(style, true);
        StyleConstants.setBackground(style, highlightColour());
        document.setCharacterAttributes(expression.start(), expression.end() - expression.start(), style, false);
    }

    private static Color highlightColour() {
        Color background = UIManager.getColor("Panel.background");
        return Contrast.blend(ThemeColors.hyperlinkColor(ThemeColors.isDark(background)),
                background == null ? Color.WHITE : background, HIGHLIGHT_SHARE);
    }

    private static JLabel warningLabel(String warning) {
        JLabel label = SwingHtmlGuard.disableHtml(new JLabel(warning));
        label.setForeground(ThemeColors.warning());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
