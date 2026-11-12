package com.mcpscanner.ui.identities;

import com.mcpscanner.auth.AccessToken;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.SignedInTokens;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.ui.widgets.CopyableSecret;
import com.mcpscanner.ui.widgets.LabeledForm;
import com.mcpscanner.ui.widgets.SecretField;
import com.mcpscanner.ui.widgets.WrappingText;

import java.awt.datatransfer.Clipboard;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

final class SignedInTokenFields {

    private final IdentityUiServices services;
    private final CopyableSecret access;
    private final CopyableSecret refresh;
    private final WrappingText help = new WrappingText("").muted();
    private List<LabeledForm.Row> rows = List.of();

    SignedInTokenFields(IdentityUiServices services, Supplier<Clipboard> clipboard) {
        this.services = services;
        this.access = new CopyableSecret("access token", clipboard);
        this.refresh = new CopyableSecret("refresh token", clipboard);
    }

    void addTo(LabeledForm form) {
        rows = List.of(form.addRow("Access token:", access), form.addRow("Refresh token:", refresh),
                form.addUnlabeledRow(help));
    }

    void seed() {
        access.secret().mask();
        refresh.secret().mask();
    }

    void refresh(Identity identity) {
        Optional<SignedInTokens> tokens = services.registry().credential(identity.id())
                .flatMap(AuthStrategy::signedInTokens);
        access.display(tokens.map(signedIn -> signedIn.accessToken().value()));
        refresh.display(tokens.flatMap(SignedInTokens::refreshToken));
        rows.get(0).setVisible(access.isVisible());
        rows.get(1).setVisible(refresh.isVisible());
        rows.get(2).setVisible(tokens.isPresent());
        help.setText(tokens.map(signedIn -> describe(signedIn, identity)).orElse(""));
    }

    SecretField accessToken() {
        return access.secret();
    }

    SecretField refreshToken() {
        return refresh.secret();
    }

    String help() {
        return help.getText();
    }

    private static String describe(SignedInTokens tokens, Identity identity) {
        return (expiry(tokens.accessToken()) + (tokens.refreshToken().isPresent()
                ? " and renews with the refresh token. " : ". ") + signsInThrough(identity)).strip();
    }

    private static String expiry(AccessToken token) {
        return token.expiresAt()
                .map(expiresAt -> "Access token expires in "
                        + Math.max(0, Duration.between(Instant.now(), expiresAt).toMinutes()) + " min")
                .orElse("Access token has no stated expiry");
    }

    private static String signsInThrough(Identity identity) {
        return identity.oauth().issuer().isBlank() ? "" : "Signs in through its own issuer.";
    }
}
