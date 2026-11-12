package com.mcpscanner.ui.detail;

import com.mcpscanner.ui.widgets.SwingHtmlGuard;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class Chip extends JLabel {

    private static final int ARC = 10;
    private static final int VERTICAL_PADDING = 1;
    private static final int HORIZONTAL_PADDING = 7;
    private static final float SIZE_REDUCTION = 1f;

    private final ChipVariant variant;

    public Chip(String text, ChipVariant variant) {
        super(text);
        this.variant = variant;
        SwingHtmlGuard.disableHtml(this);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(VERTICAL_PADDING, HORIZONTAL_PADDING, VERTICAL_PADDING,
                HORIZONTAL_PADDING));
        applyTheme();
    }

    public static Chip neutral(String text) {
        return new Chip(text, ChipVariant.NEUTRAL);
    }

    public static Chip attention(String text) {
        return new Chip(text, ChipVariant.ATTENTION);
    }

    public static Chip positive(String text) {
        return new Chip(text, ChipVariant.POSITIVE);
    }

    public ChipVariant variant() {
        return variant;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (variant != null) {
            applyTheme();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D pill = (Graphics2D) graphics.create();
        pill.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        pill.setColor(getBackground());
        pill.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
        pill.dispose();
        super.paintComponent(graphics);
    }

    private void applyTheme() {
        Color tint = variant.tintOn(ThemeBackground.panel());
        setBackground(tint);
        setForeground(variant.textOn(tint));
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            setFont(font.deriveFont(font.getSize2D() - SIZE_REDUCTION));
        }
    }
}
