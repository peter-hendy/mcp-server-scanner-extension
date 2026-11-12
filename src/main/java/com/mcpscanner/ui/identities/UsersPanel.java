package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.ui.widgets.SectionHeading;
import com.mcpscanner.ui.widgets.ThemeColors;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Optional;

public final class UsersPanel extends JPanel {

    static final String INTRO = "Connect, discovery and Scan run as the primary user. Ticked compared users are "
            + "replayed against them to find what they should not reach.";
    static final String PRIMARY_HEADING = "Primary user";
    static final String COMPARED_HEADING = "Compared users";
    static final String NO_COMPARED_USERS = "Add a user to find what one user can reach that another should not.";
    static final String ANONYMOUS_NOTE = "Anonymous sends no credentials. Cross-identity checks always compare "
            + "against it.";

    private final IdentityUiServices services;
    private final IdentityCommands commands;
    private final PrimaryUserChoice primaryChoice;
    private final IdentityTableModel model;
    private final IdentityTable table;
    private final JScrollPane tableScroll;
    private final IdentitiesToolbar toolbar;
    private final IdentityEditor primaryEditor;
    private final IdentityEditor comparedEditor;
    private final WrappingText note = new WrappingText("").muted();
    private final WrappingText error = new WrappingText("");
    private final List<SectionHeading> headings = List.of(new SectionHeading(PRIMARY_HEADING),
            new SectionHeading(COMPARED_HEADING));
    private final Runnable unsubscribe;
    private boolean reloading;

    public UsersPanel(IdentityUiServices services, GrantCredentialForms grantForms, Runnable openOAuthClient) {
        super(new BorderLayout());
        this.services = services;
        this.commands = new IdentityCommands(services, this::showError, this::select);
        this.primaryChoice = new PrimaryUserChoice(services, () -> this);
        this.model = new IdentityTableModel(services, commands);
        this.table = new IdentityTable(model);
        this.tableScroll = table.scrolled();
        this.toolbar = new IdentitiesToolbar(services, commands, this::selectedIdentity, this::showAddMenu);
        this.primaryEditor = new IdentityEditor(services, commands, primaryChoice, EditorRole.PRIMARY,
                grantForms, openOAuthClient);
        this.comparedEditor = new IdentityEditor(services, commands, primaryChoice, EditorRole.COMPARED,
                GrantCredentialForms.NONE, openOAuthClient);
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        error.setForeground(ThemeColors.error());
        error.setVisible(false);
        add(new VerticalStack(8).add(new WrappingText(INTRO).muted()).add(error)
                .add(headings.get(0)).add(primaryEditor)
                .add(headings.get(1)).add(toolbar).add(tableScroll).add(note).add(comparedEditor),
                BorderLayout.NORTH);
        table.getSelectionModel().addListSelectionListener(event -> onSelectionChanged());
        this.unsubscribe = services.onChange(() -> SwingUtilities.invokeLater(this::refresh));
        refresh();
    }

    public void refresh() {
        Optional<String> previous = selectedIdentity().map(Identity::id);
        reloading = true;
        model.reload();
        reloading = false;
        previous.flatMap(model::rowOf).ifPresentOrElse(row -> table.setRowSelectionInterval(row, row),
                table::clearSelection);
        table.fitColumns();
        table.fitHeight(tableScroll);
        primaryEditor.show(services.registry().primaryUser());
        onSelectionChanged();
    }

    public ScanAsChip scanAsChip() {
        return new ScanAsChip(services, commands);
    }

    public ConnectionIdentityLine connectionIdentityLine() {
        return new ConnectionIdentityLine(services);
    }

    public PrimaryUserPicker primaryUserPicker(Runnable openUsers) {
        return new PrimaryUserPicker(services, openUsers);
    }

    public CredentialPrompt credentialPrompt(CredentialPrompt.Actions actions) {
        return new CredentialPrompt(services, actions);
    }

    public void dispose() {
        unsubscribe.run();
    }

    public void focusPrimaryCredentials() {
        table.clearSelection();
        primaryEditor.focusCredentials();
    }

    public void addStarter(IdentityRole role) {
        commands.addStarter(role);
    }

    public void select(String identityId) {
        refresh();
        model.rowOf(identityId).ifPresent(row -> table.setRowSelectionInterval(row, row));
    }

    JTable table() {
        return table;
    }

    JPopupMenu addMenu() {
        return new AddIdentityMenu(selectedIdentity(), commands);
    }

    IdentityEditor primaryEditor() {
        return primaryEditor;
    }

    IdentityEditor comparedEditor() {
        return comparedEditor;
    }

    boolean showingComparedEditor() {
        return comparedEditor.isVisible();
    }

    String noteText() {
        return note.isVisible() ? note.getText() : "";
    }

    List<String> headings() {
        return headings.stream().map(SectionHeading::title).toList();
    }

    String errorText() {
        return error.isVisible() ? error.getText() : "";
    }

    JButton addButton() {
        return toolbar.addButton();
    }

    JButton removeButton() {
        return toolbar.removeButton();
    }

    JButton signInAllButton() {
        return toolbar.signInAllButton();
    }

    private void onSelectionChanged() {
        if (reloading) {
            return;
        }
        Optional<Identity> identity = selectedIdentity();
        toolbar.refresh(identity);
        identity.ifPresentOrElse(comparedEditor::show, comparedEditor::release);
        comparedEditor.setVisible(identity.isPresent());
        note.setText(anonymousSelected() ? ANONYMOUS_NOTE
                : services.registry().otherUsers().isEmpty() ? NO_COMPARED_USERS : "");
        note.setVisible(identity.isEmpty() && !note.getText().isEmpty());
        revalidate();
    }

    private boolean anonymousSelected() {
        int row = table.getSelectedRow();
        return row >= 0 && row < model.getRowCount() && model.rowAt(row).identity().isEmpty();
    }

    private Optional<Identity> selectedIdentity() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= model.getRowCount()) {
            return Optional.empty();
        }
        return model.rowAt(row).identity().flatMap(identity -> services.registry().find(identity.id()))
                .filter(identity -> !services.registry().isPrimary(identity.id()));
    }

    private void showAddMenu() {
        JButton anchor = toolbar.addButton();
        addMenu().show(anchor, 0, anchor.getHeight());
    }

    private void showError(String message) {
        error.setText(message == null ? "" : message);
        error.setVisible(message != null);
        revalidate();
    }
}
