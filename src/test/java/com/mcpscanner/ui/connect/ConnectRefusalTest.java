package com.mcpscanner.ui.connect;

import com.mcpscanner.client.McpHttpStatusException;
import com.mcpscanner.identity.credentials.CredentialNeed;
import com.mcpscanner.identity.credentials.CredentialNeedException;
import com.mcpscanner.core.connection.ConnectionAuthException;
import com.mcpscanner.core.connection.RecoveryResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectRefusalTest {

    @Test
    void aTypedNeedAnywhereInTheCauseChainIsReturnedAsIs() {
        CredentialNeed need = new CredentialNeed.OAuthSignInIncomplete("Client ID is required");

        assertThat(ConnectRefusal.need(new RuntimeException(new CredentialNeedException("x", need)))).contains(need);
    }

    @Test
    void anAuthenticationRefusalDuringTheHandshakeAsksForCredentials() {
        assertThat(ConnectRefusal.need(new IllegalStateException(new ConnectionAuthException(RecoveryResult.STOP))))
                .contains(new CredentialNeed.ServerRefused(ConnectRefusal.REFUSED, true));
    }

    @Test
    void anHttp401Or403NamesTheStatus() {
        assertThat(ConnectRefusal.need(new McpHttpStatusException("initialize", 401)))
                .contains(new CredentialNeed.ServerRefused("The server answered HTTP 401", true));
        assertThat(ConnectRefusal.need(new McpHttpStatusException("initialize", 403)))
                .contains(new CredentialNeed.ServerRefused("The server answered HTTP 403", true));
    }

    @Test
    void otherFailuresNeedNoCredentials() {
        assertThat(ConnectRefusal.need(new McpHttpStatusException("initialize", 500))).isEmpty();
        assertThat(ConnectRefusal.need(new IllegalStateException("Connection refused"))).isEmpty();
    }
}
