package com.mcpscanner.ui;

import com.mcpscanner.ui.sendto.SendToBinding;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class InventoryRowPopup<T> {

    static final List<KeyStroke> KEYBOARD_TRIGGERS = List.of(
            KeyStroke.getKeyStroke(KeyEvent.VK_F10, InputEvent.SHIFT_DOWN_MASK),
            KeyStroke.getKeyStroke(KeyEvent.VK_CONTEXT_MENU, 0));
    private static final String SHOW_POPUP = "showRowPopup";

    private final JTable table;
    private final InventoryTableModel<T> model;
    private SendToBinding<T> sendTo;

    InventoryRowPopup(JTable table, InventoryTableModel<T> model) {
        this.table = Objects.requireNonNull(table);
        this.model = Objects.requireNonNull(model);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                showIfPopup(event);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                showIfPopup(event);
            }
        });
        bindKeyboardTriggers();
    }

    void setSendTo(SendToBinding<T> sendTo, JComponent shortcutScope) {
        this.sendTo = Objects.requireNonNull(sendTo);
        sendTo.bindShortcuts(shortcutScope, this::selectedItem);
    }

    void refreshShortcuts() {
        if (sendTo != null) {
            sendTo.refreshShortcuts();
        }
    }

    private Optional<T> selectedItem() {
        if (table.getSelectedRowCount() != 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(model.rowAt(table.convertRowIndexToModel(table.getSelectedRow())));
    }

    JPopupMenu menuForViewRow(int viewRow) {
        if (sendTo == null || viewRow < 0 || viewRow >= table.getRowCount()) {
            return null;
        }
        return sendTo.popupFor(model.rowAt(table.convertRowIndexToModel(viewRow)));
    }

    private void bindKeyboardTriggers() {
        for (KeyStroke trigger : KEYBOARD_TRIGGERS) {
            table.getInputMap(JComponent.WHEN_FOCUSED).put(trigger, SHOW_POPUP);
        }
        table.getActionMap().put(SHOW_POPUP, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                showForSelectedRow();
            }
        });
    }

    private void showForSelectedRow() {
        int row = table.getSelectionModel().getLeadSelectionIndex();
        JPopupMenu menu = menuForViewRow(row);
        if (menu != null) {
            Rectangle cell = table.getCellRect(row, 1, true);
            menu.show(table, cell.x, cell.y + cell.height);
        }
    }

    private void showIfPopup(MouseEvent event) {
        if (!event.isPopupTrigger()) {
            return;
        }
        int row = table.rowAtPoint(event.getPoint());
        JPopupMenu menu = menuForViewRow(row);
        if (menu != null) {
            table.setRowSelectionInterval(row, row);
            menu.show(table, event.getX(), event.getY());
        }
    }
}
