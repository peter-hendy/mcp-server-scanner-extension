package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.SwingUtilities;

public final class PrimaryUserPicker extends JButton {

    static final String SWITCH_NOTE = "Picking another user cancels running scans and reconnects as them.";
    static final String EDIT_USERS = "Edit users…";

    private final IdentityUiServices services;
    private final PrimaryUserChoice choice;
    private final Runnable openUsers;
    private final Runnable unsubscribe;

    PrimaryUserPicker(IdentityUiServices services, Runnable openUsers) {
        this.services = services;
        this.choice = new PrimaryUserChoice(services, () -> this);
        this.openUsers = openUsers;
        SwingHtmlGuard.disableHtml(this);
        setToolTipText("The primary user: Connect, discovery and Scan run as this user");
        addActionListener(event -> showMenu());
        this.unsubscribe = services.onChange(() -> SwingUtilities.invokeLater(this::refresh));
        refresh();
    }

    public void refresh() {
        Identity primary = services.registry().primaryUser();
        setText(primary.name() + " · " + UserKindText.of(services, primary) + " ▾");
    }

    public void dispose() {
        unsubscribe.run();
    }

    public JPopupMenu menu() {
        JPopupMenu menu = new JPopupMenu();
        services.registry().identities().forEach(identity -> menu.add(userItem(identity)));
        menu.addSeparator();
        JMenuItem edit = new JMenuItem(EDIT_USERS);
        edit.addActionListener(event -> openUsers.run());
        menu.add(edit);
        JMenuItem note = new JMenuItem(SWITCH_NOTE);
        note.setEnabled(false);
        menu.add(note);
        return menu;
    }

    private void showMenu() {
        menu().show(this, 0, getHeight());
    }

    private JMenuItem userItem(Identity identity) {
        boolean current = choice.isCurrent(identity);
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(
                identity.name() + " · " + UserKindText.brief(services, identity), current);
        SwingHtmlGuard.disableHtml(item);
        item.setEnabled(current || choice.canChoose(identity));
        choice.blocker(identity).ifPresent(item::setToolTipText);
        item.addActionListener(event -> choice.choose(identity.id()));
        return item;
    }
}
