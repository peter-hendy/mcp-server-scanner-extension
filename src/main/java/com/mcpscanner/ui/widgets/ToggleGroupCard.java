package com.mcpscanner.ui.widgets;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.List;

public final class ToggleGroupCard extends JPanel {

    public static final String SELECTED_STATE = "JButton.selectedState";
    public static final String INDETERMINATE = "indeterminate";

    private final JCheckBox master;
    private final List<OptionRow> rows;

    public ToggleGroupCard(String title, String description, List<ToggleOption> options) {
        super(new BorderLayout());
        this.master = UiStyle.styleClass(new JCheckBox(title), UiStyle.SEMIBOLD);
        this.rows = options.stream().map(OptionRow::new).toList();
        master.addActionListener(event -> selectAll(master.isSelected()));
        rows.forEach(row -> row.onUserToggle(this::refreshMaster));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeColors.separator()),
                BorderFactory.createEmptyBorder(4, 6, 6, 8)));
        add(body(description), BorderLayout.NORTH);
        refreshMaster();
    }

    public void selectAll(boolean selected) {
        rows.forEach(row -> row.select(selected));
        refreshMaster();
    }

    private JPanel body(String description) {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(leftAligned(header(description)));
        rows.forEach(row -> body.add(leftAligned(row)));
        return body;
    }

    private JPanel header(String description) {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createEmptyBorder(0, 0, 4, 0),
                        BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeColors.separator())),
                BorderFactory.createEmptyBorder(0, 0, 4, 0)));
        header.add(master, BorderLayout.NORTH);
        if (!description.isBlank()) {
            header.add(OptionRow.hintBelow(master, description), BorderLayout.CENTER);
        }
        return header;
    }

    private static <T extends JComponent> T leftAligned(T component) {
        component.setAlignmentX(LEFT_ALIGNMENT);
        return component;
    }

    private void refreshMaster() {
        long selected = rows.stream().filter(OptionRow::isSelected).count();
        boolean all = selected == rows.size();
        master.setSelected(all);
        master.putClientProperty(SELECTED_STATE, selected > 0 && !all ? INDETERMINATE : null);
        master.repaint();
    }
}
