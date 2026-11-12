package com.mcpscanner.ui.scans;

import com.mcpscanner.engine.ScanError;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ScanErrorTableModel extends AbstractTableModel {

    private static final List<String> COLUMNS = List.of("Time", "Check", "Request", "Field", "Reason");

    private final ScanText text;
    private List<ScanError> received = List.of();
    private List<ScanError> errors = List.of();

    ScanErrorTableModel(ScanText text) {
        this.text = text;
    }

    void replace(List<ScanError> latest) {
        if (!latest.equals(received)) {
            received = List.copyOf(latest);
            List<ScanError> newestFirst = new ArrayList<>(latest);
            Collections.reverse(newestFirst);
            errors = List.copyOf(newestFirst);
            fireTableDataChanged();
        }
    }

    @Override
    public int getRowCount() {
        return errors.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.size();
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS.get(column);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return String.class;
    }

    @Override
    public Object getValueAt(int row, int column) {
        ScanError error = errors.get(row);
        return switch (column) {
            case 0 -> text.time(error.at());
            case 1 -> error.check().orElse("");
            case 2 -> error.requestUrl();
            case 3 -> error.field().orElse("");
            default -> error.reason();
        };
    }
}
