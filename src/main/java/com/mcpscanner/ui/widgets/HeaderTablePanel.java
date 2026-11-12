package com.mcpscanner.ui.widgets;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Map;

public final class HeaderTablePanel extends JPanel {

    private static final Dimension SCROLLABLE_VIEWPORT = new Dimension(400, 120);
    private static final int VALUE_COLUMN = 1;
    private static final int REMOVE_COLUMN = 2;

    private final HeaderTableModel model;
    private final JTable table;
    private final JButton addButton;
    private final RevealToggle eye;

    public HeaderTablePanel() {
        this("Header", "Value", "header", SCROLLABLE_VIEWPORT);
    }

    public HeaderTablePanel(String nameColumn, String valueColumn, String rowNoun, Dimension viewport) {
        super(new BorderLayout());
        this.model = new HeaderTableModel(nameColumn, valueColumn);
        this.table = new JTable(model);
        this.addButton = new JButton("+");
        this.addButton.setToolTipText("Add " + rowNoun);
        this.addButton.getAccessibleContext().setAccessibleName("Add " + rowNoun);
        this.eye = new RevealToggle(rowNoun + " values");

        configureTable(rowNoun, viewport);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        add(scroll, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    public Map<String, String> headers() {
        return model.headers();
    }

    public void setHeaders(Map<String, String> headers) {
        model.setHeaders(headers);
    }

    public void onChange(Runnable listener) {
        model.addTableModelListener(event -> listener.run());
    }

    public JTable table() {
        return table;
    }

    public void maskValues() {
        eye.setVisible(true);
        new MaskedValueColumn(eye).installOn(table, VALUE_COLUMN);
    }

    public void revealValues(boolean show) {
        eye.setSelected(show);
    }

    public boolean valuesRevealed() {
        return eye.isSelected();
    }

    public void hideValues() {
        revealValues(false);
    }

    public JToggleButton valuesToggle() {
        return eye;
    }

    private void configureTable(String rowNoun, Dimension viewport) {
        table.setFillsViewportHeight(true);
        table.setPreferredScrollableViewportSize(viewport);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        new RemoveButtonColumn("Remove " + rowNoun, row -> true, model::removeRow).installOn(table, REMOVE_COLUMN);
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addButton.addActionListener(e -> model.addRow());
        footer.add(addButton);
        eye.setVisible(false);
        footer.add(eye);
        return footer;
    }
}
