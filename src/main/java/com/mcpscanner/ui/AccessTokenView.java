package com.mcpscanner.ui;

import com.mcpscanner.auth.AccessToken;
import com.mcpscanner.ui.widgets.CopyableSecret;

import javax.swing.JButton;
import java.awt.datatransfer.Clipboard;
import java.util.function.Supplier;

final class AccessTokenView {

    static final String REFRESH_LABEL = "Refresh";
    static final String REFRESH_TIP = "Get a new access token using the refresh token";
    static final String SIGN_IN_AGAIN_TIP = "Sign in again in the browser to get a new access token";
    private static final String REFRESHING_LABEL = "Refreshing…";

    private final TokenRenewal renewal;
    private final CopyableSecret token;
    private final JButton refreshButton = new JButton(REFRESH_LABEL);
    private AuthenticationSummary summary;
    private Runnable onRenewalFinished = () -> { };

    AccessTokenView(TokenRenewal renewal, Supplier<Clipboard> clipboard) {
        this.renewal = renewal;
        this.token = new CopyableSecret("access token", clipboard);
        refreshButton.addActionListener(event -> renew());
        refreshButton.getAccessibleContext().setAccessibleName(REFRESH_LABEL);
    }

    void show(AuthenticationSummary summary, Runnable onRenewalFinished) {
        token.secret().mask();
        display(summary, onRenewalFinished);
    }

    CopyableSecret row() {
        return token;
    }

    JButton refreshButton() {
        return refreshButton;
    }

    boolean renewable() {
        return summary != null && summary.renewable();
    }

    private void display(AuthenticationSummary summary, Runnable onRenewalFinished) {
        this.summary = summary;
        this.onRenewalFinished = onRenewalFinished;
        token.display(summary.token().map(AccessToken::value));
        refreshButton.setVisible(summary.renewable());
        refreshButton.setToolTipText(summary.refreshToken().isPresent() ? REFRESH_TIP : SIGN_IN_AGAIN_TIP);
    }

    private void renew() {
        refreshButton.setEnabled(false);
        refreshButton.setText(REFRESHING_LABEL);
        renewal.start(summary, () -> {
            refreshButton.setEnabled(true);
            refreshButton.setText(REFRESH_LABEL);
            display(summary, onRenewalFinished);
            onRenewalFinished.run();
        });
    }
}
