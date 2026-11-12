package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.StaleConnection;
import com.mcpscanner.ui.widgets.WarningBanner;

import java.util.Optional;

final class StaleConnectionBanner extends WarningBanner {

    private final IdentityUiServices services;

    StaleConnectionBanner(IdentityUiServices services) {
        super("");
        this.services = services;
        setVisible(false);
    }

    void refresh(Identity identity) {
        Optional<StaleConnection> stale = services.staleConnection()
                .filter(current -> current.user().id().equals(identity.id()));
        stale.map(ConnectionIdentityText::pointerToHeader).ifPresent(this::setMessage);
        setVisible(stale.isPresent());
    }
}
