package com.mcpscanner.ui.identities;

import com.mcpscanner.identity.ConnectedIdentity;
import com.mcpscanner.identity.StaleConnection;

final class ConnectionIdentityText {

    private ConnectionIdentityText() {
    }

    static final String RECONNECT = "Reconnect";

    static String line(ConnectedIdentity connected) {
        String user = "Connected as " + connected.name();
        return connected.anonymous() ? user + " · no credentials"
                : user + " · " + AuthLabels.connectedKind(connected.authKind());
    }

    static String hint(StaleConnection stale) {
        return stale.user().name() + "'s credentials changed since you connected";
    }

    static String pointerToHeader(StaleConnection stale) {
        return hint(stale) + ". Use " + RECONNECT + " at the top.";
    }
}
