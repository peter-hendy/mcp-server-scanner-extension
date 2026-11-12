package com.mcpscanner.ui;

import com.mcpscanner.ui.widgets.PrimaryActionButton;
import com.mcpscanner.ui.widgets.ThemeColors;
import com.mcpscanner.ui.widgets.WrapLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;

final class ScanBar extends JPanel {

    private static final int GAP = 8;
    private static final int EDGE = 12;

    private final PrimaryActionButton scanButton = new PrimaryActionButton("Scan");
    private final JLabel scanHint = new JLabel();
    private final JButton checksSummary = new JButton();
    private final PausedTasksNotice pausedTasks;

    ScanBar(JComponent scanActivity, JComponent compareChip, JComponent supportLinks, JComponent scanModeButton,
            PausedTasksNotice pausedTasks) {
        super(new BorderLayout(GAP, GAP / 2));
        this.pausedTasks = pausedTasks;
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeColors.separator()),
                BorderFactory.createEmptyBorder(GAP / 2, EDGE, GAP / 2, EDGE)));
        scanButton.setMnemonic('S');
        checksSummary.putClientProperty("JButton.buttonType", "borderless");
        scanHint.setForeground(ThemeColors.muted());
        scanHint.setVisible(false);
        JPanel scanControls = new JPanel(new WrapLayout(FlowLayout.TRAILING, GAP, 0));
        scanControls.add(checksSummary);
        scanControls.add(scanHint);
        scanControls.add(compareChip);
        scanControls.add(splitScanButton(scanModeButton));
        scanControls.add(scanActivity);
        add(pausedTasks.banner(), BorderLayout.NORTH);
        add(verticallyCentred(supportLinks), BorderLayout.WEST);
        add(scanControls, BorderLayout.CENTER);
    }

    private JPanel splitScanButton(JComponent scanModeButton) {
        JPanel split = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        split.add(scanButton);
        split.add(scanModeButton);
        return split;
    }

    private static JPanel verticallyCentred(JComponent component) {
        JPanel centred = new JPanel(new GridBagLayout());
        centred.add(component);
        return centred;
    }

    PrimaryActionButton scanButton() {
        return scanButton;
    }

    JLabel scanHint() {
        return scanHint;
    }

    JButton checksSummary() {
        return checksSummary;
    }

    void refreshPausedTasks() {
        pausedTasks.refresh();
    }

    void showScanHint(String hint) {
        scanHint.setText(hint == null ? "" : hint);
        scanHint.setVisible(hint != null);
    }
}
