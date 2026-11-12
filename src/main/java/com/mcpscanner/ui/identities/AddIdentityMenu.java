package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.Optional;

final class AddIdentityMenu extends JPopupMenu {

    AddIdentityMenu(Optional<Identity> selected, IdentityCommands commands) {
        add(item("Bearer token", commands::addBearer));
        add(item("Custom headers", commands::addCustomHeaders));
        add(item("OAuth sign-in", commands::addOAuth));
        add(item("No credentials", commands::addNone));
        addSeparator();
        add(duplicate(selected, commands));
    }

    private static JMenuItem duplicate(Optional<Identity> selected, IdentityCommands commands) {
        JMenuItem item = new JMenuItem(selected.map(identity -> "Duplicate " + identity.name()).orElse("Duplicate"));
        item.setEnabled(selected.isPresent());
        selected.ifPresent(identity -> item.addActionListener(event -> commands.duplicate(identity.id())));
        return item;
    }

    private static JMenuItem item(String text, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(event -> action.run());
        return item;
    }
}
