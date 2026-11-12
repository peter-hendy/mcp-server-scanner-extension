package com.mcpscanner.ui.widgets;

import javax.swing.JToggleButton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

final class SideTab extends JToggleButton {

    private static final String ALERT_MARK = " ●";
    private static final int HOVER_BAR_INSET = 4;

    private String title;
    private String text;
    private boolean alerting;

    SideTab(String title) {
        this.title = title;
        this.text = title;
        setRolloverEnabled(true);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        getAccessibleContext().setAccessibleName(title);
        showPlain();
    }

    void relabel(String label) {
        title = label;
        getAccessibleContext().setAccessibleName(label);
        if (alerting) {
            text = label + ALERT_MARK;
            refresh();
        } else {
            showPlain();
        }
    }

    String text() {
        return text;
    }

    void showAlert(String reason) {
        alerting = true;
        text = title + ALERT_MARK;
        setToolTipText(reason);
        getAccessibleContext().setAccessibleDescription(reason);
        refresh();
    }

    void showPlain() {
        alerting = false;
        text = title;
        setToolTipText("Show or hide " + title);
        getAccessibleContext().setAccessibleDescription(null);
        refresh();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setFont(SideTabStyle.font(getFont()));
        setForeground(alerting ? ThemeColors.error() : null);
    }

    @Override
    public Dimension getPreferredSize() {
        Insets padding = SideTabStyle.padding();
        int across = Math.max(SideTabStyle.thickness(), metrics().getHeight() + padding.top + padding.bottom);
        return new Dimension(across, metrics().stringWidth(text) + padding.left + padding.right);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            paintBackground(canvas);
            paintAccent(canvas);
            paintLabel(canvas);
        } finally {
            canvas.dispose();
        }
    }

    private void paintBackground(Graphics2D canvas) {
        Color background = isSelected() ? SideTabStyle.selectedBackground() : null;
        if (background != null) {
            canvas.setColor(background);
            canvas.fillRect(0, 0, getWidth(), getHeight());
        }
    }

    private void paintAccent(Graphics2D canvas) {
        if (isSelected()) {
            canvas.setColor(SideTabStyle.accent());
            canvas.fillRect(0, 0, SideTabStyle.accentThickness(), getHeight());
        } else if (getModel().isRollover()) {
            canvas.setColor(SideTabStyle.hoverAccent());
            canvas.fillRect(0, HOVER_BAR_INSET, SideTabStyle.accentThickness(), getHeight() - 2 * HOVER_BAR_INSET);
        }
    }

    private void paintLabel(Graphics2D canvas) {
        FontMetrics metrics = metrics();
        canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        canvas.setFont(getFont());
        canvas.setColor(labelColor());
        int x = (getWidth() - metrics.getHeight()) / 2 + metrics.getDescent();
        int y = (getHeight() - metrics.stringWidth(text)) / 2;
        canvas.translate(x, y);
        canvas.rotate(Math.PI / 2);
        canvas.drawString(text, 0, 0);
    }

    Color labelColor() {
        if (alerting) {
            return getForeground();
        }
        if (getModel().isRollover()) {
            return SideTabStyle.hoverForeground(getForeground());
        }
        return isSelected() ? SideTabStyle.selectedForeground(getForeground()) : getForeground();
    }

    private FontMetrics metrics() {
        return getFontMetrics(getFont());
    }

    private void refresh() {
        setForeground(alerting ? ThemeColors.error() : null);
        revalidate();
        repaint();
    }
}
