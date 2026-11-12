package com.mcpscanner.ui.access;

import com.mcpscanner.access.AccessFilter;
import com.mcpscanner.ui.widgets.ThemeColors;
import com.mcpscanner.ui.widgets.WrapLayout;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;

final class AccessToolbar extends JPanel {

    static final String LEGEND = "● listed in that identity's own list   ○ hidden   ✓ call succeeded   "
            + "✗ refused   ⚠ breaks the expectation   " + AccessInfoNote.NOT_YET_CHECKED + ": not replayed yet";

    private final JComboBox<AccessFilter> filter = new JComboBox<>(AccessFilter.values());
    private final JButton refresh = new JButton("Refresh as all users");
    private final JButton checkAccess;
    private final JLabel scope;
    private final NoComparisonHint noComparisonHint;

    AccessToolbar(CheckAccessControl checkAccess, Runnable refreshListings, Runnable filterChanged) {
        super(new WrapLayout(FlowLayout.LEADING, 8, 4));
        this.checkAccess = checkAccess.button();
        this.scope = checkAccess.scope();
        this.noComparisonHint = checkAccess.noComparisonHint();
        filter.addActionListener(event -> filterChanged.run());
        refresh.setToolTipText("Run tools/list and resources/list again as every compared identity");
        refresh.addActionListener(event -> refreshListings.run());
        JLabel legend = new JLabel(LEGEND);
        legend.setForeground(ThemeColors.muted());
        add(new JLabel("Show:"));
        add(filter);
        add(this.checkAccess);
        add(scope);
        add(noComparisonHint);
        add(refresh);
        add(Box.createHorizontalStrut(8));
        add(legend);
    }

    AccessFilter filter() {
        return (AccessFilter) filter.getSelectedItem();
    }

    JComboBox<AccessFilter> filterCombo() {
        return filter;
    }

    JButton checkButton() {
        return checkAccess;
    }

    JLabel scopeLabel() {
        return scope;
    }

    NoComparisonHint noComparisonHint() {
        return noComparisonHint;
    }

    JButton refreshButton() {
        return refresh;
    }

    void enableRefresh(boolean connected) {
        refresh.setEnabled(connected);
    }
}
