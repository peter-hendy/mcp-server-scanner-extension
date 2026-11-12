package com.mcpscanner.ui;

import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.credentials.CredentialNeed;
import com.mcpscanner.ui.state.ConnectAttempt;
import com.mcpscanner.ui.state.ConnectPhase;
import com.mcpscanner.ui.state.ConnectionStatus;
import com.mcpscanner.ui.state.UiConnectionState;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CredentialPromptPresenterTest {

    private static final CredentialNeed MISSING =
            new CredentialNeed.MissingCredential(IdentityAuthKind.BEARER, "Enter a bearer token for User 1");
    private static final CredentialNeed REFUSED = new CredentialNeed.ServerRefused("The server answered HTTP 401", true);

    private final CredentialPromptPresenter presenter = new CredentialPromptPresenter();

    @Test
    void whileDisconnectedAMissingPrimaryCredentialIsShown() {
        assertThat(presenter.need(UiConnectionState.disconnected(), Optional.of(MISSING), 0)).contains(MISSING);
        assertThat(presenter.need(UiConnectionState.disconnected(), Optional.empty(), 0)).isEmpty();
    }

    @Test
    void aFailedConnectThatNeedsCredentialsWinsUntilTheNextAttempt() {
        presenter.connectFailed(REFUSED);
        UiConnectionState failed = UiConnectionState.failed(ConnectPhase.CONNECT, "refused");

        assertThat(presenter.need(failed, Optional.of(MISSING), 0)).contains(REFUSED);

        presenter.connectStarted();
        assertThat(presenter.need(failed, Optional.empty(), 0)).isEmpty();
    }

    @Test
    void nothingIsAskedWhileConnecting() {
        presenter.connectFailed(REFUSED);
        UiConnectionState connecting = UiConnectionState.connecting(mock(ConnectAttempt.class), () -> { },
                ConnectPhase.CONNECT, "Connecting…");

        assertThat(presenter.need(connecting, Optional.of(MISSING), 0)).isEmpty();
    }

    @Test
    void whileConnectedOnlyRefusedToolCallsAreShown() {
        UiConnectionState connected = UiConnectionState.connected(mock(ConnectResult.class), "host",
                ConnectionStatus.disconnected(), null);

        assertThat(presenter.need(connected, Optional.of(MISSING), 0)).isEmpty();
        assertThat(presenter.need(connected, Optional.of(MISSING), 3))
                .contains(new CredentialNeed.ToolCallsRefused(3));
    }
}
