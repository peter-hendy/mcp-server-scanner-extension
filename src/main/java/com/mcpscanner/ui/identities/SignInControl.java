package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.credentials.SignInStatus;
import com.mcpscanner.ui.widgets.ThemeColors;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.EnumSet;
import java.util.Set;

final class SignInControl extends JPanel {

    private static final Set<SignInStatus.State> IN_PROGRESS =
            EnumSet.of(SignInStatus.State.QUEUED, SignInStatus.State.SIGNING_IN);

    private final IdentityUiServices services;
    private final IdentityCommands commands;
    private final IdentityStatusText statusText;
    private final BoundIdentity bound;
    private final JButton button = new JButton("Sign in");
    private final JLabel status = new JLabel();

    SignInControl(IdentityUiServices services, IdentityCommands commands, BoundIdentity bound) {
        super(new FlowLayout(FlowLayout.LEADING, 8, 0));
        this.services = services;
        this.commands = commands;
        this.statusText = new IdentityStatusText(services);
        this.bound = bound;
        button.setToolTipText("Opens the browser with a fresh login prompt. Sign in as this identity's account.");
        button.addActionListener(event -> bound.current().ifPresent(this::toggle));
        add(button);
        add(status);
    }

    void refresh(Identity identity) {
        boolean inProgress = inProgress(identity);
        button.setText(inProgress ? "Cancel" : signInLabel(identity));
        String text = statusText.signIn(identity);
        status.setText(text);
        status.setForeground(text.startsWith(IdentityStatusText.READY_MARK) ? ThemeColors.success()
                : text.startsWith(IdentityStatusText.WARNING_MARK) ? ThemeColors.warning() : ThemeColors.muted());
    }

    JButton button() {
        return button;
    }

    String statusText() {
        return status.getText();
    }

    private void toggle(Identity identity) {
        if (inProgress(identity)) {
            commands.cancelSignIn(identity.id());
        } else {
            commands.signIn(identity.id());
        }
    }

    private String signInLabel(Identity identity) {
        return services.registry().credential(identity.id()).isPresent() ? "Sign in again" : "Sign in";
    }

    private boolean inProgress(Identity identity) {
        return IN_PROGRESS.contains(services.signIns().status(identity.id()).state());
    }
}
