package com.mcpscanner.ui.state;

import java.util.ArrayList;
import java.util.List;

public final class UiStateReducer {

    private static final String SEE_LOG = "Open Log below for details";
    private static final String TOKEN_REFRESH_REJECTED = "token refresh rejected";
    private static final String SIGN_IN_AGAIN = "Connect to sign in again";
    private static final List<UiSideEffect> SESSION_TEARDOWN = List.of(
            UiSideEffect.RunDisconnect.INSTANCE,
            UiSideEffect.ClearOAuthStrategy.INSTANCE,
            UiSideEffect.ClearInventory.INSTANCE);

    public Reduction reduce(UiConnectionState state, UiAction action) {
        Reduction reduction = reduceAction(state, action);
        return reduction.precededBy(lifecycleEffects(state, action, reduction.state()));
    }

    private static List<UiSideEffect> lifecycleEffects(UiConnectionState previous, UiAction action,
                                                       UiConnectionState next) {
        if (next != previous && isTerminal(next)) {
            return List.of(UiSideEffect.ReleaseConnectionAuth.INSTANCE, UiSideEffect.RetireConnection.INSTANCE);
        }
        if (action instanceof UiAction.ConnectRequested && next != previous) {
            return List.of(UiSideEffect.ReleaseConnectionAuth.INSTANCE);
        }
        return List.of();
    }

    private static boolean isTerminal(UiConnectionState state) {
        return state instanceof UiConnectionState.Disconnected || state instanceof UiConnectionState.Failed;
    }

    private Reduction reduceAction(UiConnectionState state, UiAction action) {
        if (action instanceof UiAction.ConnectRequested cr) {
            return reduceConnectRequested(state, cr);
        }
        if (action instanceof UiAction.ConnectRejected cr) {
            return reduceConnectRejected(state, cr);
        }
        if (action instanceof UiAction.ConnectWorkerAttached attached) {
            return reduceConnectWorkerAttached(state, attached);
        }
        if (action instanceof UiAction.ConnectProgress progress) {
            return reduceConnectProgress(state, progress);
        }
        return reduceSettlement(state, action);
    }

    private Reduction reduceSettlement(UiConnectionState state, UiAction action) {
        if (action instanceof UiAction.ConnectSucceeded succeeded) {
            return reduceConnectSucceeded(state, succeeded);
        }
        if (action instanceof UiAction.ConnectFailed failed) {
            return reduceConnectFailed(state, failed);
        }
        if (action instanceof UiAction.DisconnectRequested) {
            return reduceDisconnectRequested(state);
        }
        if (action instanceof UiAction.AuthTerminallyFailed) {
            return reduceAuthTerminallyFailed(state);
        }
        if (action instanceof UiAction.ExternalDisconnect) {
            return reduceExternalDisconnect(state);
        }
        throw new IllegalArgumentException("Unhandled UI action: " + action);
    }

    private Reduction reduceConnectRequested(UiConnectionState state, UiAction.ConnectRequested action) {
        if (state instanceof UiConnectionState.Disconnected
                || state instanceof UiConnectionState.Failed
                || state instanceof UiConnectionState.Connected) {
            UiConnectionState next = UiConnectionState.connecting(
                    action.attempt(), Cancellable.NOOP, ConnectPhase.CONNECT, "Connecting…");
            return new Reduction(next, List.of(new UiSideEffect.SpawnConnectWorker(action.attempt())));
        }
        return ignored(state, "ConnectRequested", state);
    }

    private Reduction reduceConnectRejected(UiConnectionState state, UiAction.ConnectRejected action) {
        if (state instanceof UiConnectionState.Connecting) {
            return ignored(state, "ConnectRejected", state);
        }
        UiConnectionState failed = UiConnectionState.failed(action.phase(), action.reason());
        if (state instanceof UiConnectionState.Connected) {
            return new Reduction(failed, SESSION_TEARDOWN);
        }
        return new Reduction(failed, List.of());
    }

    private Reduction reduceConnectWorkerAttached(UiConnectionState state,
                                                  UiAction.ConnectWorkerAttached action) {
        if (state instanceof UiConnectionState.Connecting connecting) {
            return new Reduction(connecting.withCancellable(action.cancellable()), List.of());
        }
        return ignored(state, "ConnectWorkerAttached", state);
    }

    private Reduction reduceConnectProgress(UiConnectionState state, UiAction.ConnectProgress action) {
        if (state instanceof UiConnectionState.Connecting connecting) {
            return new Reduction(connecting.withProgress(action.phase(), action.message()), List.of());
        }
        return ignored(state, "ConnectProgress", state);
    }

    private Reduction reduceConnectSucceeded(UiConnectionState state, UiAction.ConnectSucceeded action) {
        if (state instanceof UiConnectionState.Connecting) {
            UiConnectionState connected = UiConnectionState.connected(
                    action.result(), action.host(), action.status(), action.connectedAs());
            List<UiSideEffect> effects = new ArrayList<>();
            effects.add(new UiSideEffect.PopulateInventory(action.result()));
            if (action.oauthStrategy() != null) {
                effects.add(new UiSideEffect.PublishOAuthStrategy(action.oauthStrategy()));
            }
            return new Reduction(connected, List.copyOf(effects));
        }
        return ignored(state, "ConnectSucceeded", state);
    }

    private Reduction reduceConnectFailed(UiConnectionState state, UiAction.ConnectFailed action) {
        if (state instanceof UiConnectionState.Connecting connecting) {
            return new Reduction(
                    UiConnectionState.failed(connecting.phase(), action.reason(), SEE_LOG),
                    List.of(UiSideEffect.ClearAutoDiscoveredOAuthFields.INSTANCE));
        }
        return ignored(state, "ConnectFailed", state);
    }

    private Reduction reduceDisconnectRequested(UiConnectionState state) {
        if (state instanceof UiConnectionState.Disconnected) {
            return new Reduction(state, List.of());
        }
        if (state instanceof UiConnectionState.Connecting connecting) {
            return new Reduction(
                    UiConnectionState.cancelled(),
                    List.of(
                            new UiSideEffect.CancelConnect(connecting.cancellable()),
                            UiSideEffect.RunDisconnect.INSTANCE,
                            UiSideEffect.ClearOAuthStrategy.INSTANCE,
                            UiSideEffect.ClearAutoDiscoveredOAuthFields.INSTANCE,
                            UiSideEffect.ClearInventory.INSTANCE));
        }
        return new Reduction(
                UiConnectionState.disconnected(),
                List.of(
                        UiSideEffect.RunDisconnect.INSTANCE,
                        UiSideEffect.ClearOAuthStrategy.INSTANCE,
                        UiSideEffect.ClearAutoDiscoveredOAuthFields.INSTANCE,
                        UiSideEffect.ClearInventory.INSTANCE));
    }

    private Reduction reduceExternalDisconnect(UiConnectionState state) {
        if (!(state instanceof UiConnectionState.Connected)) {
            return new Reduction(state, List.of());
        }
        return new Reduction(
                UiConnectionState.connectionLost(),
                List.of(UiSideEffect.ClearOAuthStrategy.INSTANCE, UiSideEffect.ClearInventory.INSTANCE));
    }

    private Reduction reduceAuthTerminallyFailed(UiConnectionState state) {
        if (!(state instanceof UiConnectionState.Connected)) {
            return ignored(state, "AuthTerminallyFailed", state);
        }
        return new Reduction(
                UiConnectionState.failed(ConnectPhase.OAUTH, TOKEN_REFRESH_REJECTED, SIGN_IN_AGAIN),
                SESSION_TEARDOWN);
    }

    private static Reduction ignored(UiConnectionState state, String actionName, UiConnectionState current) {
        String message = "Ignored " + actionName + " in state " + current.getClass().getSimpleName();
        return new Reduction(state, List.of(new UiSideEffect.LogWarning(message)));
    }

    public record Reduction(UiConnectionState state, List<UiSideEffect> sideEffects) {

        Reduction precededBy(List<UiSideEffect> leadingEffects) {
            if (leadingEffects.isEmpty()) {
                return this;
            }
            List<UiSideEffect> combined = new ArrayList<>(leadingEffects);
            combined.addAll(sideEffects);
            return new Reduction(state, List.copyOf(combined));
        }
    }
}
