package com.mcpscanner.ui;

import com.mcpscanner.ui.state.ConnectionState;
import com.mcpscanner.ui.state.ConnectionStatus;
import com.mcpscanner.ui.widgets.DotIcon;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;
import com.mcpscanner.ui.widgets.ThemeColors;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.Color;
import java.awt.Dimension;
import java.util.function.Supplier;

public class StatusCluster extends JPanel {

    private static final int BUSY_WIDTH = 48;
    private static final int GAP = 8;

    private final DotIcon dotIcon = new DotIcon(ThemeColors::muted);
    private final JLabel label = SwingHtmlGuard.disableHtml(new JLabel());
    private final JProgressBar busyIndicator = new JProgressBar();

    public StatusCluster() {
        setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
        setOpaque(false);
        label.setIcon(dotIcon);
        label.setMinimumSize(new Dimension(0, label.getPreferredSize().height));
        busyIndicator.setIndeterminate(true);
        busyIndicator.setMaximumSize(new Dimension(BUSY_WIDTH, busyIndicator.getPreferredSize().height));
        busyIndicator.setPreferredSize(new Dimension(BUSY_WIDTH, busyIndicator.getPreferredSize().height));
        busyIndicator.getAccessibleContext().setAccessibleName("Connecting");
        add(label);
        add(Box.createHorizontalStrut(GAP));
        add(busyIndicator);
        add(Box.createHorizontalGlue());
        update(ConnectionStatus.disconnected());
    }

    public void update(ConnectionStatus status) {
        dotIcon.setColor(colorFor(status.state()));
        label.setText(status.message());
        label.setToolTipText(SwingHtmlGuard.escapeHtml(status.message()));
        label.getAccessibleContext().setAccessibleName("Connection status: " + status.message());
        busyIndicator.setVisible(status.state() == ConnectionState.CONNECTING);
        revalidate();
        repaint();
    }

    Color dotColor() {
        return dotIcon.color();
    }

    public String statusText() {
        return label.getText();
    }

    String tooltipText() {
        return label.getToolTipText();
    }

    Object labelHtmlDisabled() {
        return label.getClientProperty("html.disable");
    }

    boolean busyIndicatorVisible() {
        return busyIndicator.isVisible();
    }

    private static Supplier<Color> colorFor(ConnectionState state) {
        return switch (state) {
            case DISCONNECTED -> ThemeColors::muted;
            case CONNECTING -> ThemeColors::warning;
            case CONNECTED -> ThemeColors::success;
            case FAILED -> ThemeColors::error;
        };
    }
}
