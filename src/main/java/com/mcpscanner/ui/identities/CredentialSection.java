package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.credentials.CredentialRequirement;
import com.mcpscanner.ui.widgets.HeaderTablePanel;
import com.mcpscanner.ui.widgets.LabeledForm;
import com.mcpscanner.ui.widgets.SecretField;
import com.mcpscanner.ui.widgets.ThemeColors;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextField;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CredentialSection {

    private static final Dimension HEADER_VIEWPORT = new Dimension(240, 60);
    private static final String TOKEN_PROMPT = "Paste a bearer token";

    private final IdentityUiServices services;
    private final IdentityCommands commands;
    private final BoundIdentity bound;
    private final SecretField token = new SecretField("token");
    private final HeaderTablePanel headers = new HeaderTablePanel("Header", "Value", "header", HEADER_VIEWPORT);
    private final OwnIssuerFields ownIssuer;
    private final SignInControl signIn;
    private final SignedInTokenFields signedIn;
    private final WrappingText note = new WrappingText("");
    private final GrantCredentialForms grantForms;
    private final OAuthClientLink oauthClientLink;
    private LabeledForm.Row oauthClientRow;
    private LabeledForm.Row tokenRow;
    private LabeledForm.Row headersRow;
    private LabeledForm.Row signInRow;
    private LabeledForm.Row noteRow;

    CredentialSection(IdentityUiServices services, IdentityCommands commands, BoundIdentity bound,
                      GrantCredentialForms grantForms, Runnable openOAuthClient) {
        this.services = services;
        this.commands = commands;
        this.bound = bound;
        this.grantForms = grantForms;
        this.oauthClientLink = new OAuthClientLink(openOAuthClient);
        this.ownIssuer = new OwnIssuerFields(commands, bound);
        this.signIn = new SignInControl(services, commands, bound);
        this.signedIn = new SignedInTokenFields(services, () -> Toolkit.getDefaultToolkit().getSystemClipboard());
        note.setForeground(ThemeColors.warning());
        headers.maskValues();
        headers.setMinimumSize(new Dimension(0, headers.getPreferredSize().height));
        token.setPlaceholder(TOKEN_PROMPT);
        token.onChange(this::supplyToken);
        headers.onChange(this::supplyHeaders);
    }

    void addTo(LabeledForm form) {
        oauthClientRow = form.addUnlabeledRow(oauthClientLink);
        grantForms.addTo(form);
        tokenRow = form.addRow("Token:", token);
        headersRow = form.addRow("Headers:", headers);
        form.addFullWidthRow(ownIssuer.component());
        signInRow = form.addCompactRow("Sign in:", signIn);
        signedIn.addTo(form);
        noteRow = form.addUnlabeledRow(note);
    }

    void seed(Identity identity) {
        token.setValue(services.credentials().heldToken(identity.id()).orElse(""));
        token.mask();
        headers.setHeaders(heldValues(identity));
        headers.hideValues();
        ownIssuer.seed(identity);
        signedIn.seed();
    }

    void showHeld(Identity identity) {
        String held = services.credentials().heldToken(identity.id()).orElse("");
        if (!token.textField().isFocusOwner() && !held.equals(token.value())) {
            token.setValue(held);
            token.mask();
        }
        Map<String, String> heldHeaders = heldValues(identity);
        if (!headers.table().isEditing() && !heldHeaders.equals(headers.headers())) {
            headers.setHeaders(heldHeaders);
            headers.hideValues();
        }
    }

    void refresh(Identity identity) {
        CredentialRequirement requirement = services.credentials().requirement(identity.id());
        tokenRow.setVisible(requirement instanceof CredentialRequirement.TokenRequired);
        headersRow.setVisible(requirement instanceof CredentialRequirement.HeaderValuesRequired
                || identity.authKind() == IdentityAuthKind.CUSTOM_HEADERS);
        ownIssuer.setVisible(identity.authKind() == IdentityAuthKind.OAUTH);
        oauthClientRow.setVisible(OAuthClientLink.appliesTo(identity));
        grantForms.show(identity.authKind());
        signInRow.setVisible(requirement instanceof CredentialRequirement.BrowserSignInRequired);
        noteRow.setVisible(requirement instanceof CredentialRequirement.Unavailable);
        note.setText(requirement instanceof CredentialRequirement.Unavailable unavailable ? unavailable.reason() : "");
        signIn.refresh(identity);
        signedIn.refresh(identity);
    }

    JComponent focusTarget(JComponent fallback) {
        if (token.isVisible()) {
            return token.textField();
        }
        return headers.isVisible() ? headers.table() : fallback;
    }

    OAuthClientLink oauthClientLink() {
        return oauthClientLink;
    }

    SecretField tokenField() {
        return token;
    }

    HeaderTablePanel headerValues() {
        return headers;
    }

    JTextField issuerField() {
        return ownIssuer.issuerField();
    }

    JButton signInButton() {
        return signIn.button();
    }

    String signInStatus() {
        return signIn.statusText();
    }

    SecretField accessToken() {
        return signedIn.accessToken();
    }

    SecretField refreshToken() {
        return signedIn.refreshToken();
    }

    String tokenHelp() {
        return signedIn.help();
    }

    String note() {
        return note.getText();
    }

    private void supplyToken() {
        bound.ifEditing(identity -> {
            String typed = token.value();
            commands.supplyCredential(() -> {
                if (typed.isBlank()) {
                    services.credentials().clear(identity.id());
                } else {
                    services.credentials().supplyToken(identity.id(), typed);
                }
            });
        });
    }

    private void supplyHeaders() {
        bound.ifEditing(identity -> {
            Map<String, String> entered = headers.headers();
            List<String> names = List.copyOf(entered.keySet());
            if (identity.authKind() == IdentityAuthKind.CUSTOM_HEADERS && !names.equals(identity.headerNames())
                    && !commands.update(identity.withHeaderNames(names))) {
                return;
            }
            commands.supplyCredential(() -> supplyHeaderValues(identity, entered));
        });
    }

    private void supplyHeaderValues(Identity identity, Map<String, String> entered) {
        if (entered.isEmpty() || entered.containsValue("")) {
            services.credentials().clear(identity.id());
        } else {
            services.credentials().supplyHeaderValues(identity.id(), entered);
        }
    }

    private List<String> headerNames(Identity identity) {
        CredentialRequirement requirement = services.credentials().requirement(identity.id());
        return requirement instanceof CredentialRequirement.HeaderValuesRequired required
                ? required.headerNames() : identity.headerNames();
    }

    private Map<String, String> heldValues(Identity identity) {
        Map<String, String> held = services.credentials().heldHeaderValues(identity.id());
        Map<String, String> values = new LinkedHashMap<>();
        headerNames(identity).forEach(name -> values.put(name, held.getOrDefault(name, "")));
        return values;
    }
}
