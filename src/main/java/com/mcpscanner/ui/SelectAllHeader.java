package com.mcpscanner.ui;

import com.mcpscanner.ui.widgets.ThemeColors;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.TableModelEvent;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.stream.IntStream;

final class SelectAllHeader {

    enum State { NONE, PARTIAL, ALL }

    private static final String TOGGLE_ACTION = "toggleAllTicks";
    private static final int FOCUS_BORDER_WIDTH = 2;

    private final JTable table;
    private final TableModel model;
    private final int column;
    private final String plural;
    private boolean enabled = true;

    SelectAllHeader(JTable table, TableModel model, int column, String plural) {
        this.table = table;
        this.model = model;
        this.column = column;
        this.plural = plural;
    }

    void install() {
        JTableHeader header = table.getTableHeader();
        table.getColumnModel().getColumn(table.convertColumnIndexToView(column))
                .setHeaderRenderer(new Renderer());
        header.addMouseListener(new ClickListener());
        installKeyboardToggle(header);
        installFocusRepaint(header);
        header.getAccessibleContext().setAccessibleName("Tick all " + plural + " for scanning");
        installRefreshTriggers();
        refresh();
    }

    private void installKeyboardToggle(JTableHeader header) {
        header.setFocusable(true);
        header.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), TOGGLE_ACTION);
        header.getActionMap().put(TOGGLE_ACTION, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                toggle();
            }
        });
    }

    private static void installFocusRepaint(JTableHeader header) {
        header.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                header.repaint();
            }

            @Override
            public void focusLost(FocusEvent event) {
                header.repaint();
            }
        });
    }

    private void installRefreshTriggers() {
        table.getRowSorter().addRowSorterListener(event -> refresh());
        model.addTableModelListener(event -> {
            if (touchesTickColumn(event)) {
                refresh();
            }
        });
    }

    private boolean touchesTickColumn(TableModelEvent event) {
        boolean rowUpdate = event.getType() == TableModelEvent.UPDATE && event.getLastRow() != Integer.MAX_VALUE;
        return rowUpdate && (event.getColumn() == column || event.getColumn() == TableModelEvent.ALL_COLUMNS);
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    void toggle() {
        if (!enabled) {
            return;
        }
        boolean ticked = state() != State.ALL;
        for (int modelRow : visibleModelRows()) {
            model.setValueAt(ticked, modelRow, column);
        }
    }

    State state() {
        int[] visible = visibleModelRows();
        long ticked = java.util.Arrays.stream(visible).filter(this::isTicked).count();
        if (ticked == 0) {
            return State.NONE;
        }
        return ticked == visible.length ? State.ALL : State.PARTIAL;
    }

    private int[] visibleModelRows() {
        return IntStream.range(0, table.getRowCount())
                .map(table::convertRowIndexToModel)
                .filter(modelRow -> model.isCellEditable(modelRow, column))
                .toArray();
    }

    private boolean isTicked(int modelRow) {
        return Boolean.TRUE.equals(model.getValueAt(modelRow, column));
    }

    void refresh() {
        JTableHeader header = table.getTableHeader();
        header.getAccessibleContext().setAccessibleDescription(describe(state(), "press Space to"));
        header.repaint();
    }

    private String describe(State state, String gesture) {
        return switch (state) {
            case ALL -> "All shown " + plural + " ticked — " + gesture + " untick them";
            case NONE -> "No shown " + plural + " ticked — " + gesture + " tick them all";
            case PARTIAL -> "Some shown " + plural + " ticked — " + gesture + " tick them all";
        };
    }

    private boolean isSelectColumn(int viewColumn) {
        return viewColumn >= 0
                && table.convertColumnIndexToModel(viewColumn) == column;
    }

    private final class ClickListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent event) {
            if (isSelectColumn(table.getTableHeader().columnAtPoint(event.getPoint()))) {
                toggle();
            }
        }
    }

    private final class Renderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JTableHeader header = table.getTableHeader();
            HeaderCell cell = new HeaderCell(state(), header.isFocusOwner());
            cell.setBackground(header.getBackground());
            cell.setForeground(header.getForeground());
            cell.setToolTipText(describe(state(), "click or press Space to"));
            return cell;
        }
    }

    private static final class HeaderCell extends JPanel {
        private final State state;

        HeaderCell(State state, boolean focused) {
            super(new BorderLayout());
            this.state = state;
            setOpaque(true);
            setBorder(focused ? focusBorder() : separatorBorder());
            JCheckBox box = new JCheckBox();
            box.setHorizontalAlignment(SwingConstants.CENTER);
            box.setOpaque(false);
            box.setSelected(state == State.ALL);
            add(box, BorderLayout.CENTER);
        }

        @Override
        public void paint(Graphics g) {
            super.paint(g);
            if (state != State.PARTIAL) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getForeground());
                int dashWidth = Math.max(8, getWidth() / 4);
                g2.fillRect((getWidth() - dashWidth) / 2, getHeight() / 2 - 1, dashWidth, 2);
            } finally {
                g2.dispose();
            }
        }

        private static Border focusBorder() {
            Color focus = UIManager.getColor("Component.focusColor");
            return BorderFactory.createLineBorder(focus != null ? focus : ThemeColors.muted(), FOCUS_BORDER_WIDTH);
        }

        private static Border separatorBorder() {
            return BorderFactory.createMatteBorder(0, 0, 1, 1, separatorColor());
        }

        private static Color separatorColor() {
            Color color = UIManager.getColor("TableHeader.bottomSeparatorColor");
            if (color != null) {
                return color;
            }
            Color fallback = UIManager.getColor("Separator.foreground");
            return fallback != null ? fallback : ThemeColors.muted();
        }
    }
}
