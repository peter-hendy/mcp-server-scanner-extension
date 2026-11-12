package com.mcpscanner.ui.access;

import com.mcpscanner.access.mining.AccessMining;
import com.mcpscanner.access.mining.MiningPreferences;
import com.mcpscanner.access.mining.WriteToolOptIn;

import javax.swing.JButton;
import javax.swing.JComboBox;
import java.awt.Component;
import java.util.Optional;
import java.util.Set;

final class WriteToolOptInControl {

    private final AccessMining mining;
    private final MiningDialogs dialogs;
    private final Component parent;
    private final JComboBox<WriteToolChoice> choice = new JComboBox<>(WriteToolChoice.values());
    private final JButton choose = new JButton("Choose tools…");

    WriteToolOptInControl(AccessMining mining, MiningDialogs dialogs, Component parent) {
        this.mining = mining;
        this.dialogs = dialogs;
        this.parent = parent;
        choice.setSelectedItem(WriteToolChoice.of(preferences().writeTools().mode()));
        choice.setToolTipText("Collecting calls only read-only tools unless you allow write tools here");
        choice.addActionListener(event -> chosen());
        choose.addActionListener(event -> chooseTools());
        showChooseButton();
    }

    JComboBox<WriteToolChoice> choice() {
        return choice;
    }

    JButton chooseButton() {
        return choose;
    }

    private void chosen() {
        WriteToolChoice picked = (WriteToolChoice) choice.getSelectedItem();
        switch (picked) {
            case READ_ONLY -> preferences().setWriteTools(WriteToolOptIn.NONE);
            case ALL -> preferences().setWriteTools(WriteToolOptIn.ALL);
            case SELECTED -> {
                if (preferences().writeTools().mode() != WriteToolOptIn.Mode.SELECTED && !chooseTools()) {
                    choice.setSelectedItem(WriteToolChoice.of(preferences().writeTools().mode()));
                }
            }
        }
        showChooseButton();
    }

    private boolean chooseTools() {
        Optional<Set<String>> picked = dialogs.chooseWriteTools(parent, mining.writeTools(),
                preferences().writeTools().selected());
        picked.ifPresent(tools -> preferences().setWriteTools(WriteToolOptIn.selected(tools)));
        return picked.isPresent();
    }

    private void showChooseButton() {
        choose.setVisible(choice.getSelectedItem() == WriteToolChoice.SELECTED);
    }

    private MiningPreferences preferences() {
        return mining.preferences();
    }
}
