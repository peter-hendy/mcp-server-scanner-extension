package com.mcpscanner.ui;

import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.detail.DetailLabels;
import com.mcpscanner.ui.detail.RowMenu;
import com.mcpscanner.ui.widgets.PopupTrigger;
import com.mcpscanner.ui.widgets.UiStyle;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

final class PromptArgumentGrid extends JPanel {

    private static final String NONE_GIVEN = "None given";
    private static final int ROW_GAP = 8;
    private static final int COLUMN_GAP = 16;
    private static final int COLUMNS = 3;

    record Row(JComponent argument, JComponent requirement, WrappingText description) {
    }

    private final List<Row> rows = new ArrayList<>();
    private int gridRow;

    PromptArgumentGrid() {
        super(new GridBagLayout());
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    void show(List<PromptArgumentEntry> entries, RowMenu argumentMenu) {
        removeAll();
        rows.clear();
        gridRow = 0;
        addHeadings();
        entries.forEach(entry -> addRow(rowFor(entry), event -> argumentMenu.menuFor(entry.name())));
        revalidate();
        repaint();
    }

    List<Row> rows() {
        return List.copyOf(rows);
    }

    private void addHeadings() {
        add(heading("Argument"), cell(0, 0));
        add(heading("Required"), cell(1, 0));
        add(heading("Description"), cell(2, 1));
        gridRow++;
        GridBagConstraints separator = cell(0, 1);
        separator.gridwidth = COLUMNS;
        add(new JSeparator(), separator);
        gridRow++;
    }

    private void addRow(Row row, Function<MouseEvent, Optional<JPopupMenu>> menu) {
        List.of(row.argument(), row.requirement(), row.description())
                .forEach(cell -> PopupTrigger.installThroughout(cell, menu));
        add(row.argument(), cell(0, 0));
        add(row.requirement(), cell(1, 0));
        add(row.description(), cell(2, 1));
        rows.add(row);
        gridRow++;
    }

    private static Row rowFor(PromptArgumentEntry entry) {
        return new Row(argumentCell(entry), requirementCell(entry.argument().required()),
                descriptionCell(entry.argument().description()));
    }

    private static JComponent argumentCell(PromptArgumentEntry entry) {
        JPanel cell = new JPanel();
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        cell.setOpaque(false);
        entry.title().ifPresentOrElse(
                title -> {
                    cell.add(DetailLabels.bold(DetailLabels.literal(title)));
                    cell.add(UiStyle.monospaced(DetailLabels.muted(entry.name())));
                },
                () -> cell.add(DetailLabels.bold(UiStyle.monospaced(DetailLabels.literal(entry.name())))));
        return cell;
    }

    private static JComponent requirementCell(boolean required) {
        return required ? Chip.attention("required") : DetailLabels.muted("optional");
    }

    private static WrappingText descriptionCell(String description) {
        boolean absent = description == null || description.isBlank();
        WrappingText text = new WrappingText(absent ? NONE_GIVEN : description);
        return absent ? text.muted() : text;
    }

    private static JLabel heading(String text) {
        return DetailLabels.bold(DetailLabels.muted(text));
    }

    private GridBagConstraints cell(int column, double weight) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = gridRow;
        constraints.weightx = weight;
        constraints.fill = weight > 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        constraints.insets = new Insets(0, 0, ROW_GAP, column < COLUMNS - 1 ? COLUMN_GAP : 0);
        return constraints;
    }
}
