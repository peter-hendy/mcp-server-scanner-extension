package com.mcpscanner.ui;

import com.mcpscanner.auth.grants.ClientAuthenticationProviderRegistry;
import com.mcpscanner.auth.grants.EnterpriseIdentityProviderRegistry;
import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.ui.widgets.DocumentChanges;
import com.mcpscanner.ui.widgets.LabeledForm;
import com.mcpscanner.ui.widgets.ThemeColors;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.event.DocumentListener;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

final class OptionalGrantFields {

    private final OptionalGrantRules rules;
    private final JTextField expectedIssuer = new JTextField(25);
    private final JTextField clientId = new JTextField(25);
    private final JPasswordField clientSecret = new JPasswordField(25);
    private final JTextField scopes = new JTextField(25);
    private final JComboBox<String> authentication = new JComboBox<>();
    private final JComboBox<String> signerProvider = new JComboBox<>();
    private final JComboBox<String> identityProvider = new JComboBox<>();
    private final List<LabeledForm.Row> rows = new ArrayList<>();
    private LabeledForm.Row clientSecretRow;
    private LabeledForm.Row signerProviderRow;
    private Runnable changeListener = () -> { };
    private boolean shown;

    OptionalGrantFields(OptionalGrantSettings.Profile profile, ClientAuthenticationProviderRegistry signers,
                        EnterpriseIdentityProviderRegistry identityProviders) {
        this.rules = new OptionalGrantRules(profile, signers, identityProviders.ids());
        rules.authenticationMethods().forEach(authentication::addItem);
        populateProviders(signerProvider, signers.ids());
        populateProviders(identityProvider, identityProviders.ids());
        describeFields();
        wireChangeListeners();
    }

    void addTo(LabeledForm form) {
        addPluginWarning(form);
        rows.add(required(form.addRow("Trusted issuer URL:", expectedIssuer)));
        rows.add(required(form.addRow("Client ID:", clientId)));
        rows.add(form.addRow("Client authentication:", authentication));
        clientSecretRow = required(form.addRow("Client secret:", clientSecret));
        signerProviderRow = required(form.addRow("JWT signer provider ID:", signerProvider));
        rows.add(form.addRow("Scopes:", scopes));
        if (rules.isEnterprise()) {
            rows.add(required(form.addRow("Identity provider ID:", identityProvider)));
        }
        setShown(shown);
    }

    void setShown(boolean shown) {
        this.shown = shown;
        rows.forEach(row -> row.setVisible(shown));
        updateAuthenticationFields();
    }

    boolean available() {
        return !rules.identityProviderMissing();
    }

    JTextField clientIdField() {
        return clientId;
    }

    JTextField issuerField() {
        return expectedIssuer;
    }

    JPasswordField secretField() {
        return clientSecret;
    }

    void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    Optional<String> blocker() {
        char[] secret = clientSecret.getPassword();
        try {
            return rules.blocker(input(secret));
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    OptionalGrantSettings snapshot() {
        char[] secret = clientSecret.getPassword();
        try {
            return rules.settings(input(secret));
        } finally {
            Arrays.fill(secret, '\0');
        }
    }

    void clear() {
        List.of(expectedIssuer, clientId, scopes).forEach(field -> field.setText(""));
        clearSecret();
    }

    void clearSecret() {
        clientSecret.setText("");
    }

    private OptionalGrantInput input(char[] secret) {
        return new OptionalGrantInput(expectedIssuer.getText(), clientId.getText(), secret, scopes.getText(),
                selectedMethod(), selected(signerProvider), selected(identityProvider));
    }

    private String selectedMethod() {
        return (String) authentication.getSelectedItem();
    }

    private static String selected(JComboBox<String> combo) {
        return Optional.ofNullable((String) combo.getSelectedItem()).orElse("");
    }

    private static void populateProviders(JComboBox<String> combo, List<String> ids) {
        combo.addItem("");
        ids.forEach(combo::addItem);
        if (ids.size() == 1) {
            combo.setSelectedIndex(1);
        }
    }

    private static LabeledForm.Row required(LabeledForm.Row row) {
        row.setRequired(true);
        return row;
    }

    private void describeFields() {
        authentication.setRenderer(new AuthenticationMethodRenderer());
        authentication.setToolTipText("How the scanner proves the client's identity to the token endpoint");
        signerProvider.setToolTipText("Installed signer plugin that creates the private_key_jwt client assertion");
        identityProvider.setToolTipText("Installed identity provider plugin that obtains the enterprise ID token");
        clientSecret.setToolTipText("Not saved");
        scopes.setToolTipText("Space separated");
    }

    private void addPluginWarning(LabeledForm form) {
        if (!rules.identityProviderMissing()) {
            return;
        }
        JLabel pluginWarning = new JLabel("No enterprise identity provider plugin is installed; this grant cannot connect.");
        pluginWarning.setForeground(ThemeColors.error());
        rows.add(form.addFullWidthRow(pluginWarning));
    }

    private void wireChangeListeners() {
        DocumentListener onText = DocumentChanges.onChange(() -> changeListener.run());
        List.of(expectedIssuer, clientId, clientSecret, scopes)
                .forEach(field -> field.getDocument().addDocumentListener(onText));
        authentication.addActionListener(event -> {
            updateAuthenticationFields();
            changeListener.run();
        });
        signerProvider.addActionListener(event -> changeListener.run());
        identityProvider.addActionListener(event -> changeListener.run());
    }

    private void updateAuthenticationFields() {
        boolean usesSecret = OptionalGrantRules.usesClientSecret(selectedMethod());
        boolean usesSigner = OptionalGrantRules.usesSigner(selectedMethod());
        clientSecret.setEnabled(usesSecret);
        signerProvider.setEnabled(usesSigner);
        if (clientSecretRow != null) {
            clientSecretRow.setVisible(shown && usesSecret);
            signerProviderRow.setVisible(shown && usesSigner);
        }
        if (!usesSecret) {
            clearSecret();
        }
    }

    private static final class AuthenticationMethodRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            var cell = (JLabel) super.getListCellRendererComponent(list, friendlyName(value), index, selected, focused);
            cell.setToolTipText(value == null ? null : value.toString());
            return cell;
        }

        private static String friendlyName(Object method) {
            if (method == null) {
                return "";
            }
            return switch (method.toString()) {
                case OptionalGrantRules.BASIC -> "Secret in header";
                case OptionalGrantRules.POST -> "Secret in body";
                case OptionalGrantRules.PRIVATE_KEY_JWT -> "Signed JWT";
                case OptionalGrantRules.PUBLIC_CLIENT -> "None (public)";
                default -> method.toString();
            };
        }
    }
}
