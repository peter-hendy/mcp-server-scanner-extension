package com.mcpscanner.ui.widgets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class WarningBanner extends JPanel {

    private static final double TINT = 0.14;

    private final WrappingText message;
    private final JButton action;

    public WarningBanner(String message) {
        this(message, null);
    }

    public WarningBanner(String message, JButton action) {
        super(new GridBagLayout());
        this.message = new WrappingText(message);
        this.action = action;
        Color warning = ThemeColors.warning();
        setBackground(tint());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, warning),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        add(icon(warning), at(0, 0, 0, new Insets(0, 0, 0, 8), GridBagConstraints.NONE));
        add(this.message, at(1, 0, 1, new Insets(0, 0, 0, 0), GridBagConstraints.HORIZONTAL));
        if (action != null) {
            add(action, at(1, 1, 0, new Insets(8, 0, 0, 0), GridBagConstraints.NONE));
        }
    }

    public static Color tint() {
        return blend(UIManager.getColor("Panel.background"), ThemeColors.warning());
    }

    public void setMessage(String text) {
        message.setText(text);
    }

    public String message() {
        return message.getText();
    }

    public JButton actionButton() {
        return action;
    }

    private static JLabel icon(Color warning) {
        JLabel icon = new JLabel("▲");
        icon.setForeground(warning);
        return icon;
    }

    private static GridBagConstraints at(int x, int y, double weight, Insets insets, int fill) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.weightx = weight;
        constraints.fill = fill;
        constraints.insets = insets;
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        return constraints;
    }

    private static Color blend(Color base, Color tint) {
        Color from = base == null ? Color.WHITE : base;
        return new Color(mix(from.getRed(), tint.getRed()), mix(from.getGreen(), tint.getGreen()),
                mix(from.getBlue(), tint.getBlue()));
    }

    private static int mix(int base, int tint) {
        return (int) Math.round(base + (tint - base) * TINT);
    }
}
