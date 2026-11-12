package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.StaleConnection;

import java.awt.Component;
import java.util.Optional;
import java.util.function.Supplier;

final class PrimaryUserChoice {

    private final IdentityUiServices services;
    private final CredentialReadiness readiness;
    private final Supplier<Component> dialogParent;

    PrimaryUserChoice(IdentityUiServices services, Supplier<Component> dialogParent) {
        this.services = services;
        this.readiness = new CredentialReadiness(services);
        this.dialogParent = dialogParent;
    }

    Optional<String> blocker(Identity identity) {
        return readiness.connectAsBlocker(identity);
    }

    boolean isCurrent(Identity identity) {
        return services.registry().isPrimary(identity.id());
    }

    boolean canChoose(Identity identity) {
        return !isCurrent(identity) && blocker(identity).isEmpty();
    }

    void choose(String identityId) {
        services.registry().find(identityId).filter(this::canChoose).ifPresent(this::switchTo);
    }

    void bringUpToDate(StaleConnection stale) {
        reconnectAs(stale.user(), () -> { });
    }

    private void switchTo(Identity chosen) {
        reconnectAs(chosen, () -> services.registry().makePrimary(chosen.id()));
    }

    private void reconnectAs(Identity user, Runnable switchUser) {
        if (!hasSession()) {
            switchUser.run();
        } else if (confirmReconnect(user)) {
            services.connection().reconnectAs(switchUser);
        }
    }

    private boolean hasSession() {
        IdentityConnection connection = services.connection();
        return connection.isConnected() || connection.isConnecting();
    }

    private boolean confirmReconnect(Identity chosen) {
        return services.reconnectPrompt().confirm(dialogParent.get(), chosen.name(),
                services.registry().primaryUser().name());
    }
}
