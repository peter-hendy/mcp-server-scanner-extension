package com.mcpscanner.ui.widgets;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BorderLayout;

public final class OptionRow extends JPanel {

    private static final int FALLBACK_ICON_WIDTH = 16;

    private final ToggleOption option;
    private final JCheckBox checkbox;

    public OptionRow(ToggleOption option) {
        super(new BorderLayout());
        this.option = option;
        this.checkbox = new JCheckBox(option.label(), option.selected());
        checkbox.addActionListener(event -> option.onChange().accept(checkbox.isSelected()));
        setOpaque(false);
        add(checkbox, BorderLayout.NORTH);
        if (!option.hint().isBlank()) {
            add(hintBelow(checkbox, option.hint()), BorderLayout.CENTER);
        }
    }

    public boolean isSelected() {
        return checkbox.isSelected();
    }

    public void select(boolean selected) {
        if (checkbox.isSelected() != selected) {
            checkbox.setSelected(selected);
            option.onChange().accept(selected);
        }
    }

    public void onUserToggle(Runnable listener) {
        checkbox.addActionListener(event -> listener.run());
    }

    static WrappingText hintBelow(JCheckBox checkbox, String text) {
        WrappingText hint = new WrappingText(text).muted();
        hint.setBorder(BorderFactory.createEmptyBorder(0, labelIndent(checkbox), 2, 0));
        return hint;
    }

    private static int labelIndent(JCheckBox checkbox) {
        Icon icon = UIManager.getIcon("CheckBox.icon");
        int iconWidth = icon == null ? FALLBACK_ICON_WIDTH : icon.getIconWidth();
        return checkbox.getInsets().left + iconWidth + checkbox.getIconTextGap();
    }
}
