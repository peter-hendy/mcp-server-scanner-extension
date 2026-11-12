package com.mcpscanner.ui;

import com.mcpscanner.identity.credentials.CredentialNeed;
import com.mcpscanner.ui.state.UiConnectionState;

import java.util.Optional;

final class CredentialPromptPresenter {

    private Optional<CredentialNeed> failure = Optional.empty();

    void connectFailed(CredentialNeed need) {
        failure = Optional.of(need);
    }

    void connectStarted() {
        failure = Optional.empty();
    }

    Optional<CredentialNeed> need(UiConnectionState state, Optional<CredentialNeed> idleNeed, int refusedToolCalls) {
        if (state instanceof UiConnectionState.Connecting) {
            return Optional.empty();
        }
        if (state instanceof UiConnectionState.Connected) {
            return refusedToolCalls > 0
                    ? Optional.of(new CredentialNeed.ToolCallsRefused(refusedToolCalls)) : Optional.empty();
        }
        return failure.or(() -> idleNeed);
    }
}
