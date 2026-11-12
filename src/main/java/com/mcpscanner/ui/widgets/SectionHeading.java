package com.mcpscanner.ui.widgets;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class SectionHeading extends JPanel {

    private final JLabel title;

    public SectionHeading(String text) {
        super(new GridBagLayout());
        setOpaque(false);
        title = new JLabel(text);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        GridBagConstraints label = new GridBagConstraints();
        label.insets = new Insets(0, 0, 0, 8);
        add(title, label);
        JSeparator rule = new JSeparator();
        rule.setForeground(ThemeColors.separator());
        GridBagConstraints line = new GridBagConstraints();
        line.gridx = 1;
        line.weightx = 1;
        line.fill = GridBagConstraints.HORIZONTAL;
        add(rule, line);
    }

    public String title() {
        return title.getText();
    }
}
