package com.mcpscanner.ui;

import com.mcpscanner.scan.ScanMode;
import com.mcpscanner.scan.ScanModeSetting;

import com.mcpscanner.ui.widgets.SwingHtmlGuard;
import com.mcpscanner.ui.widgets.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import java.util.EnumMap;
import java.util.Map;

final class ScanModeSelector {

    private static final String DROPDOWN_ARROW = " \u25BE";
    private static final String MODE_PREFIX = "Mode: ";
    private static final int DESCRIPTION_INDENT = 28;

    private final ScanModeSetting setting;
    private final JButton button = new JButton();
    private final JPopupMenu menu = new JPopupMenu();
    private final Map<ScanMode, JRadioButtonMenuItem> items = new EnumMap<>(ScanMode.class);

    ScanModeSelector(ScanModeSetting setting) {
        this.setting = setting;
        addModeItems();
        button.addActionListener(event -> menu.show(button, 0, button.getHeight()));
        refresh();
    }

    JButton button() {
        return button;
    }

    JPopupMenu menu() {
        return menu;
    }

    void refresh() {
        ScanMode current = setting.current();
        ScanModeText text = ScanModeText.of(current);
        items.get(current).setSelected(true);
        button.setText(MODE_PREFIX + text.shortLabel() + DROPDOWN_ARROW);
        button.setToolTipText(text.tooltip());
    }

    private void addModeItems() {
        ButtonGroup group = new ButtonGroup();
        for (ScanMode mode : ScanMode.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(ScanModeText.of(mode).menuLabel());
            item.addActionListener(event -> setting.select(mode));
            group.add(item);
            menu.add(item);
            menu.add(description(mode));
            items.put(mode, item);
        }
    }

    private static JLabel description(ScanMode mode) {
        JLabel description = SwingHtmlGuard.disableHtml(new JLabel(ScanModeText.of(mode).description()));
        description.setForeground(ThemeColors.muted());
        description.setBorder(BorderFactory.createEmptyBorder(0, DESCRIPTION_INDENT, 4, 8));
        return description;
    }
}
