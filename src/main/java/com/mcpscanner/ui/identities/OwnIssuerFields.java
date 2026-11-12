package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityOAuthSettings;
import com.mcpscanner.ui.widgets.Disclosure;
import com.mcpscanner.ui.widgets.DocumentChanges;
import com.mcpscanner.ui.widgets.LabeledForm;

import javax.swing.JTextField;
import java.util.Arrays;
import java.util.List;

final class OwnIssuerFields {

    static final String TITLE = "Own issuer, client ID and scopes for this user";
    private static final int LABEL_WIDTH = 80;

    private final IdentityCommands commands;
    private final BoundIdentity bound;
    private final JTextField issuer = new JTextField();
    private final JTextField clientId = new JTextField();
    private final JTextField scopes = new JTextField();
    private final Disclosure disclosure;

    OwnIssuerFields(IdentityCommands commands, BoundIdentity bound) {
        this.commands = commands;
        this.bound = bound;
        issuer.putClientProperty("JTextField.placeholderText", "https://auth.example.com");
        clientId.putClientProperty("JTextField.placeholderText", "Blank: register dynamically");
        scopes.putClientProperty("JTextField.placeholderText", "Space-separated");
        List.of(issuer, clientId, scopes).forEach(field ->
                field.getDocument().addDocumentListener(DocumentChanges.onChange(this::persist)));
        LabeledForm form = new LabeledForm(LABEL_WIDTH).withoutBottomPadding();
        form.setOpaque(false);
        form.addRow("Issuer:", issuer);
        form.addRow("Client ID:", clientId);
        form.addRow("Scopes:", scopes);
        disclosure = new Disclosure(TITLE, form);
    }

    Disclosure component() {
        return disclosure;
    }

    void seed(Identity identity) {
        IdentityOAuthSettings settings = identity.oauth();
        issuer.setText(settings.issuer());
        clientId.setText(settings.clientId());
        scopes.setText(String.join(" ", settings.scopes()));
    }

    void setVisible(boolean visible) {
        disclosure.setVisible(visible);
    }

    JTextField issuerField() {
        return issuer;
    }

    private void persist() {
        bound.ifEditing(identity -> commands.update(identity.withOAuth(new IdentityOAuthSettings(
                issuer.getText().strip(), clientId.getText().strip(), scopeList()))));
    }

    private List<String> scopeList() {
        return Arrays.stream(scopes.getText().strip().split("\\s+")).filter(scope -> !scope.isEmpty()).toList();
    }
}
