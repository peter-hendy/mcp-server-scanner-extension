package com.mcpscanner.ui.connect;

import com.mcpscanner.client.HttpAuthResponses;
import com.mcpscanner.client.McpHttpStatusException;
import com.mcpscanner.identity.credentials.CredentialNeed;
import com.mcpscanner.identity.credentials.CredentialNeedException;
import com.mcpscanner.core.connection.ConnectionAuthException;

import java.util.Optional;
import java.util.stream.Stream;

public final class ConnectRefusal {

    static final String REFUSED = "The server refused the connection for lack of valid credentials";

    private ConnectRefusal() {
    }

    public static Optional<CredentialNeed> need(Throwable failure) {
        return causes(failure).map(ConnectRefusal::needShownBy).flatMap(Optional::stream).findFirst();
    }

    private static Optional<CredentialNeed> needShownBy(Throwable cause) {
        if (cause instanceof CredentialNeedException needed) {
            return Optional.of(needed.need());
        }
        if (cause instanceof ConnectionAuthException) {
            return Optional.of(new CredentialNeed.ServerRefused(REFUSED, true));
        }
        if (cause instanceof McpHttpStatusException http && HttpAuthResponses.isAuthChallenge(http.status())) {
            return Optional.of(new CredentialNeed.ServerRefused("The server answered HTTP " + http.status(), true));
        }
        return Optional.empty();
    }

    private static Stream<Throwable> causes(Throwable failure) {
        return Stream.iterate(failure, cause -> cause != null,
                cause -> cause.getCause() == cause ? null : cause.getCause());
    }
}
