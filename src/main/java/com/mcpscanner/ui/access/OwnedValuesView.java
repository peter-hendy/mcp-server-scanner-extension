package com.mcpscanner.ui.access;

import com.mcpscanner.identity.EnteredValue;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.OwnedValue;
import com.mcpscanner.identity.OwnedValueStore;
import com.mcpscanner.ui.widgets.Edt;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;
import com.mcpscanner.ui.widgets.WholeRowScrollPane;
import com.mcpscanner.ui.widgets.WrapLayout;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

final class OwnedValuesView extends JPanel {

    private final OwnedValueStore store;
    private final OwnedValueEdits edits;
    private final Supplier<List<Identity>> users;
    private final MiningDialogs dialogs;
    private final JComboBox<UserChoice> user = new JComboBox<>();
    private final OwnedValuesTableModel model = new OwnedValuesTableModel();
    private final JTable table = new JTable(model);
    private final JButton add = new JButton("Add…");
    private final JButton edit = new JButton("Edit…");
    private final JButton delete = new JButton("Delete");
    private final JButton clear = new JButton("Clear collected IDs…");
    private final Runnable storeChanged = () -> Edt.run(this::refresh);

    OwnedValuesView(OwnedValueStore store, Supplier<List<Identity>> users, MiningDialogs dialogs) {
        super(new BorderLayout());
        this.store = store;
        this.edits = new OwnedValueEdits(store);
        this.users = users;
        this.dialogs = dialogs;
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        SwingHtmlGuard.guardStringColumns(table);
        table.getSelectionModel().addListSelectionListener(event -> enableRowActions());
        user.addActionListener(event -> showSelectedUser());
        add.addActionListener(event -> addValue());
        edit.addActionListener(event -> editValue());
        delete.addActionListener(event -> deleteValue());
        clear.addActionListener(event -> clearMined());
        add(toolbar(), BorderLayout.NORTH);
        add(new WholeRowScrollPane(table), BorderLayout.CENTER);
        store.addListener(storeChanged);
        refresh();
    }

    void dispose() {
        store.removeListener(storeChanged);
    }

    void refresh() {
        Optional<String> selected = selectedUser().map(UserChoice::id);
        List<UserChoice> choices = users.get().stream().map(UserChoice::of).toList();
        user.removeAllItems();
        choices.forEach(user::addItem);
        choices.stream().filter(choice -> selected.filter(choice.id()::equals).isPresent()).findFirst()
                .ifPresent(user::setSelectedItem);
        showSelectedUser();
    }

    JComboBox<UserChoice> userCombo() {
        return user;
    }

    JTable table() {
        return table;
    }

    JButton addButton() {
        return add;
    }

    JButton editButton() {
        return edit;
    }

    JButton deleteButton() {
        return delete;
    }

    JButton clearButton() {
        return clear;
    }

    private JPanel toolbar() {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEADING, 8, 4));
        row.add(new JLabel("IDs for:"));
        row.add(user);
        row.add(add);
        row.add(edit);
        row.add(delete);
        row.add(clear);
        return row;
    }

    private void showSelectedUser() {
        model.show(selectedUser().map(choice -> store.valuesOf(choice.id()).all()).orElse(List.of()));
        add.setEnabled(selectedUser().isPresent());
        clear.setEnabled(selectedUser().isPresent());
        enableRowActions();
    }

    private void enableRowActions() {
        Optional<OwnedValue> row = selectedValue();
        edit.setEnabled(row.filter(EnteredValue.class::isInstance).isPresent());
        delete.setEnabled(row.isPresent());
    }

    private void addValue() {
        selectedUser().ifPresent(choice -> dialogs.enterValue(this, Optional.empty())
                .ifPresent(value -> edits.add(choice.id(), value)));
    }

    private void editValue() {
        Optional<UserChoice> choice = selectedUser();
        Optional<EnteredValue> current = selectedValue().filter(EnteredValue.class::isInstance)
                .map(EnteredValue.class::cast);
        if (choice.isPresent() && current.isPresent()) {
            dialogs.enterValue(this, current)
                    .ifPresent(replacement -> edits.replace(choice.get().id(), current.get(), replacement));
        }
    }

    private void deleteValue() {
        Optional<UserChoice> choice = selectedUser();
        selectedValue().ifPresent(value -> choice.ifPresent(owner -> edits.delete(owner.id(), value)));
    }

    private void clearMined() {
        selectedUser().ifPresent(choice -> {
            switch (dialogs.clearMined(this, choice.name())) {
                case THIS_USER -> store.clearMined(choice.id());
                case ALL_USERS -> store.clearAllMined();
                case CANCEL -> { }
            }
        });
    }

    private Optional<UserChoice> selectedUser() {
        return Optional.ofNullable((UserChoice) user.getSelectedItem()).filter(Objects::nonNull);
    }

    private Optional<OwnedValue> selectedValue() {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : model.at(table.convertRowIndexToModel(row));
    }
}
