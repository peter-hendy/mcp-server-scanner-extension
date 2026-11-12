package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.GridLayout;
import java.util.Optional;
import java.util.function.Supplier;

final class IdentitiesToolbar extends JPanel {

    private final IdentityUiServices services;
    private final JButton add = new JButton("Add user ▾");
    private final JButton signInAll = new JButton("Sign in all");
    private final JButton remove = new JButton("Remove");

    IdentitiesToolbar(IdentityUiServices services, IdentityCommands commands, Supplier<Optional<Identity>> selected,
                      Runnable showAddMenu) {
        super(new GridLayout(1, 3, 6, 0));
        this.services = services;
        add.setToolTipText("Bearer token, custom headers, OAuth sign-in, or no credentials");
        signInAll.setToolTipText("Runs each OAuth user's sign-in one after another");
        add.addActionListener(event -> showAddMenu.run());
        signInAll.addActionListener(event -> commands.signInAll());
        remove.addActionListener(event -> selected.get().ifPresent(identity -> commands.remove(identity.id())));
        add(add);
        add(signInAll);
        add(remove);
    }

    void refresh(Optional<Identity> selected) {
        remove.setEnabled(selected.isPresent());
        signInAll.setEnabled(!services.registry().identities().isEmpty());
    }

    JButton addButton() {
        return add;
    }

    JButton removeButton() {
        return remove;
    }

    JButton signInAllButton() {
        return signInAll;
    }
}
