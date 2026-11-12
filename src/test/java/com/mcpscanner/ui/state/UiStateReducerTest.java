package com.mcpscanner.ui.state;

import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.identity.ConnectedIdentity;
import com.mcpscanner.identity.CredentialFingerprint;
import com.mcpscanner.identity.IdentityAuthKind;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class UiStateReducerTest {
    private static final ConnectedIdentity CONNECTED_AS = new ConnectedIdentity(
            new CredentialFingerprint("user-1", "digest"), "User 1", IdentityAuthKind.NONE, true);


    private final UiStateReducer reducer = new UiStateReducer();

    @Test
    void connectRequestedFromDisconnectedProducesConnectingWithSpawnSideEffect() {
        ConnectAttempt attempt = sampleAttempt();

        UiStateReducer.Reduction result = reducer.reduce(
                UiConnectionState.disconnected(),
                new UiAction.ConnectRequested(attempt));

        assertThat(result.state()).isInstanceOf(UiConnectionState.Connecting.class);
        UiConnectionState.Connecting connecting = (UiConnectionState.Connecting) result.state();
        assertThat(connecting.attempt()).isSameAs(attempt);
        assertThat(connecting.cancellable()).isSameAs(Cancellable.NOOP);
        assertThat(connecting.phase()).isEqualTo(ConnectPhase.CONNECT);
        assertThat(result.sideEffects())
                .containsExactly(UiSideEffect.ReleaseConnectionAuth.INSTANCE, new UiSideEffect.SpawnConnectWorker(attempt));
    }

    @Test
    void connectRequestedFromFailedProducesConnectingWithSpawnSideEffect() {
        ConnectAttempt attempt = sampleAttempt();

        UiStateReducer.Reduction result = reducer.reduce(
                UiConnectionState.failed(ConnectPhase.CONNECT, "boom"),
                new UiAction.ConnectRequested(attempt));

        assertThat(result.state()).isInstanceOf(UiConnectionState.Connecting.class);
        assertThat(result.sideEffects())
                .containsExactly(UiSideEffect.ReleaseConnectionAuth.INSTANCE, new UiSideEffect.SpawnConnectWorker(attempt));
    }

    @Test
    void connectRequestedFromConnectingIsIgnoredAndLogsWarning() {
        UiConnectionState connecting = UiConnectionState.connecting(
                sampleAttempt(), Cancellable.NOOP, ConnectPhase.CONNECT, "Connecting…");

        UiStateReducer.Reduction result = reducer.reduce(
                connecting,
                new UiAction.ConnectRequested(sampleAttempt()));

        assertThat(result.state()).isSameAs(connecting);
        assertThat(result.sideEffects()).hasSize(1);
        assertThat(result.sideEffects().get(0)).isInstanceOf(UiSideEffect.LogWarning.class);
    }

    @Test
    void connectRejectedFromDisconnectedTransitionsToFailedWithoutSpawningWorker() {
        UiStateReducer.Reduction result = reducer.reduce(
                UiConnectionState.disconnected(),
                new UiAction.ConnectRejected(ConnectPhase.OAUTH, "scope list cannot be empty"));

        assertThat(result.state()).isEqualTo(
                UiConnectionState.failed(ConnectPhase.OAUTH, "scope list cannot be empty"));
        assertThat(result.sideEffects()).containsExactly(
                UiSideEffect.ReleaseConnectionAuth.INSTANCE,
                UiSideEffect.RetireConnection.INSTANCE);
    }

    @Test
    void connectRejectedFromConnectingIsIgnoredAndLogsWarning() {
        UiConnectionState connecting = UiConnectionState.connecting(
                sampleAttempt(), Cancellable.NOOP, ConnectPhase.CONNECT, "Connecting…");

        UiStateReducer.Reduction result = reducer.reduce(
                connecting, new UiAction.ConnectRejected(ConnectPhase.OAUTH, "bad"));

        assertThat(result.state()).isSameAs(connecting);
        assertThat(result.sideEffects().get(0)).isInstanceOf(UiSideEffect.LogWarning.class);
    }

    @Test
    void connectWorkerAttachedRecordsCancellableOnConnecting() {
        Cancellable cancellable = () -> { };
        UiConnectionState connecting = UiConnectionState.connecting(
                sampleAttempt(), Cancellable.NOOP, ConnectPhase.CONNECT, "Connecting…");

        UiStateReducer.Reduction result = reducer.reduce(
                connecting, new UiAction.ConnectWorkerAttached(cancellable));

        assertThat(result.state()).isInstanceOf(UiConnectionState.Connecting.class);
        assertThat(((UiConnectionState.Connecting) result.state()).cancellable()).isSameAs(cancellable);
        assertThat(result.sideEffects()).isEmpty();
    }

    @Test
    void connectWorkerAttachedFromDisconnectedIsIgnored() {
        UiStateReducer.Reduction result = reducer.reduce(
                UiConnectionState.disconnected(),
                new UiAction.ConnectWorkerAttached(() -> { }));

        assertThat(result.state()).isEqualTo(UiConnectionState.disconnected());
        assertThat(result.sideEffects().get(0)).isInstanceOf(UiSideEffect.LogWarning.class);
    }

    @Test
    void connectProgressUpdatesPhaseAndMessageOnConnecting() {
        ConnectAttempt attempt = sampleAttempt();
        UiConnectionState connecting = UiConnectionState.connecting(
                attempt, Cancellable.NOOP, ConnectPhase.CONNECT, "Connecting…");

        UiStateReducer.Reduction result = reducer.reduce(
                connecting, new UiAction.ConnectProgress(ConnectPhase.OAUTH, "OAuth: opening browser…"));

        UiConnectionState.Connecting updated = (UiConnectionState.Connecting) result.state();
        assertThat(updated.phase()).isEqualTo(ConnectPhase.OAUTH);
        assertThat(updated.message()).isEqualTo("OAuth: opening browser…");
        assertThat(updated.attempt()).isSameAs(attempt);
        assertThat(result.sideEffects()).isEmpty();
    }

    @Test
    void connectProgressFromDisconnectedIsIgnored() {
        UiStateReducer.Reduction result = reducer.reduce(
                UiConnectionState.disconnected(),
                new UiAction.ConnectProgress(ConnectPhase.OAUTH, "anything"));

        assertThat(result.state()).isEqualTo(UiConnectionState.disconnected());
        assertThat(result.sideEffects().get(0)).isInstanceOf(UiSideEffect.LogWarning.class);
    }

    @Test
    void connectSucceededFromConnectingTransitionsToConnectedPopulatesInventoryAndPublishesOauth() {
        ConnectResult result = sampleResult(3);
        OAuthAuthCodeStrategy oauthStrategy = Mockito.mock(OAuthAuthCodeStrategy.class);
        UiConnectionState connecting = UiConnectionState.connecting(
                sampleAttempt(), Cancellable.NOOP, ConnectPhase.OAUTH, "OAuth: opening browser…");
        ConnectionStatus connectedStatus = ConnectionStatus.connected(
                "user", "mcp.example.com", null, 3);

        UiStateReducer.Reduction reduction = reducer.reduce(
                connecting,
                new UiAction.ConnectSucceeded(result, "mcp.example.com", connectedStatus, oauthStrategy, CONNECTED_AS));

        UiConnectionState.Connected connected = (UiConnectionState.Connected) reduction.state();
        assertThat(connected.result()).isSameAs(result);
        assertThat(connected.host()).isEqualTo("mcp.example.com");
        assertThat(connected.status()).isEqualTo(connectedStatus);
        assertThat(connected.connectedAs()).isEqualTo(CONNECTED_AS);
        assertThat(reduction.sideEffects()).containsExactly(
                new UiSideEffect.PopulateInventory(result),
                new UiSideEffect.PublishOAuthStrategy(oauthStrategy));
    }

    @Test
    void connectSucceededWithoutOauthPopulatesInventoryOnly() {
        UiConnectionState connecting = UiConnectionState.connecting(
                sampleAttempt(), Cancellable.NOOP, ConnectPhase.CONNECT, "Connecting…");
        ConnectionStatus connectedStatus = ConnectionStatus.connected(
                null, "mcp.example.com", null, 0);
        ConnectResult result = sampleResult(0);

        UiStateReducer.Reduction reduction = reducer.reduce(
                connecting,
                new UiAction.ConnectSucceeded(result, "mcp.example.com", connectedStatus, null, CONNECTED_AS));

        assertThat(reduction.state()).isInstanceOf(UiConnectionState.Connected.class);
        assertThat(reduction.sideEffects())
                .containsExactly(new UiSideEffect.PopulateInventory(result));
    }

    @Test
    void connectSucceededFromDisconnectedIsIgnoredAndLogsWarning() {
        UiStateReducer.Reduction result = reducer.reduce(
                UiConnectionState.disconnected(),
                new UiAction.ConnectSucceeded(sampleResult(0), "h",
                        ConnectionStatus.connected(null, "h", null, 0), null, CONNECTED_AS));

        assertThat(result.state()).isEqualTo(UiConnectionState.disconnected());
        assertThat(result.sideEffects().get(0)).isInstanceOf(UiSideEffect.LogWarning.class);
    }

    @Test
    void connectFailedFromConnectingTransitionsToFailedAndClearsAutoDiscoveredOAuth() {
        UiConnectionState connecting = UiConnectionState.connecting(
                sampleAttempt(), Cancellable.NOOP, ConnectPhase.OAUTH, "OAuth: opening browser…");

        UiStateReducer.Reduction result = reducer.reduce(
                connecting, new UiAction.ConnectFailed("scope rejected"));

        assertThat(result.state()).isEqualTo(UiConnectionState.failed(ConnectPhase.OAUTH, "scope rejected", "Open Log below for details"));
        assertThat(result.sideEffects())
                .containsExactly(UiSideEffect.ReleaseConnectionAuth.INSTANCE, UiSideEffect.RetireConnection.INSTANCE, UiSideEffect.ClearAutoDiscoveredOAuthFields.INSTANCE);
    }

    @Test
    void connectFailedFromConnectingEmitsClearAutoDiscoveredOAuthFields() {
        UiConnectionState connecting = UiConnectionState.connecting(
                sampleAttempt(), Cancellable.NOOP, ConnectPhase.OAUTH, "OAuth: opening browser…");

        UiStateReducer.Reduction result = reducer.reduce(
                connecting, new UiAction.ConnectFailed("user cancelled"));

        assertThat(result.sideEffects())
                .contains(UiSideEffect.ClearAutoDiscoveredOAuthFields.INSTANCE);
    }

    @Test
    void connectFailedFromDisconnectedIsIgnored() {
        UiStateReducer.Reduction result = reducer.reduce(
                UiConnectionState.disconnected(),
                new UiAction.ConnectFailed("anything"));

        assertThat(result.state()).isEqualTo(UiConnectionState.disconnected());
        assertThat(result.sideEffects().get(0)).isInstanceOf(UiSideEffect.LogWarning.class);
    }

    @Test
    void disconnectRequestedFromConnectingCancelsWorkerRunsDisconnectAndClearsInventory() {
        AtomicInteger cancelCount = new AtomicInteger();
        Cancellable cancellable = cancelCount::incrementAndGet;
        UiConnectionState connecting = UiConnectionState.connecting(
                sampleAttempt(), cancellable, ConnectPhase.CONNECT, "Connecting…");

        UiStateReducer.Reduction result = reducer.reduce(
                connecting, UiAction.DisconnectRequested.INSTANCE);

        assertThat(result.state()).isEqualTo(UiConnectionState.cancelled());
        assertThat(result.sideEffects()).containsExactly(
                UiSideEffect.ReleaseConnectionAuth.INSTANCE,
                UiSideEffect.RetireConnection.INSTANCE,
                new UiSideEffect.CancelConnect(cancellable),
                UiSideEffect.RunDisconnect.INSTANCE,
                UiSideEffect.ClearOAuthStrategy.INSTANCE,
                UiSideEffect.ClearAutoDiscoveredOAuthFields.INSTANCE,
                UiSideEffect.ClearInventory.INSTANCE);
    }

    @Test
    void disconnectRequestedFromConnectedRunsDisconnectClearsOauthAndInventory() {
        UiConnectionState connected = UiConnectionState.connected(
                sampleResult(2), "mcp.example.com",
                ConnectionStatus.connected(null, "mcp.example.com", null, 2), CONNECTED_AS);

        UiStateReducer.Reduction result = reducer.reduce(
                connected, UiAction.DisconnectRequested.INSTANCE);

        assertThat(result.state()).isEqualTo(UiConnectionState.disconnected());
        assertThat(result.sideEffects()).containsExactly(
                UiSideEffect.ReleaseConnectionAuth.INSTANCE,
                UiSideEffect.RetireConnection.INSTANCE,
                UiSideEffect.RunDisconnect.INSTANCE,
                UiSideEffect.ClearOAuthStrategy.INSTANCE,
                UiSideEffect.ClearAutoDiscoveredOAuthFields.INSTANCE,
                UiSideEffect.ClearInventory.INSTANCE);
    }

    @Test
    void disconnectRequestedFromFailedRunsDisconnectClearsOauthAndInventory() {
        UiStateReducer.Reduction result = reducer.reduce(
                UiConnectionState.failed(ConnectPhase.OAUTH, "boom"),
                UiAction.DisconnectRequested.INSTANCE);

        assertThat(result.state()).isEqualTo(UiConnectionState.disconnected());
        assertThat(result.sideEffects()).contains(
                UiSideEffect.RunDisconnect.INSTANCE,
                UiSideEffect.ClearInventory.INSTANCE);
    }

    @Test
    void disconnectRequestedFromDisconnectedIsNoOp() {
        UiStateReducer.Reduction result = reducer.reduce(
                UiConnectionState.disconnected(), UiAction.DisconnectRequested.INSTANCE);

        assertThat(result.state()).isEqualTo(UiConnectionState.disconnected());
        assertThat(result.sideEffects()).isEmpty();
    }

    @Test
    void externalDisconnectFromConnectedTransitionsToDisconnectedAndClearsInventory() {
        UiConnectionState connected = UiConnectionState.connected(
                sampleResult(0), "mcp.example.com",
                ConnectionStatus.connected(null, "mcp.example.com", null, 0), CONNECTED_AS);

        UiStateReducer.Reduction result = reducer.reduce(
                connected, UiAction.ExternalDisconnect.INSTANCE);

        assertThat(result.state()).isEqualTo(UiConnectionState.connectionLost());
        assertThat(result.sideEffects()).containsExactly(
                UiSideEffect.ReleaseConnectionAuth.INSTANCE,
                UiSideEffect.RetireConnection.INSTANCE,
                UiSideEffect.ClearOAuthStrategy.INSTANCE,
                UiSideEffect.ClearInventory.INSTANCE);
    }

    @Test
    void externalDisconnectFromConnectingIsIgnored() {
        UiConnectionState connecting = UiConnectionState.connecting(
                sampleAttempt(), Cancellable.NOOP, ConnectPhase.CONNECT, "Connecting…");

        UiStateReducer.Reduction result = reducer.reduce(connecting, UiAction.ExternalDisconnect.INSTANCE);

        assertThat(result.state()).isSameAs(connecting);
        assertThat(result.sideEffects()).isEmpty();
    }

    @Test
    void externalDisconnectFromDisconnectedIsNoOp() {
        UiStateReducer.Reduction result = reducer.reduce(
                UiConnectionState.disconnected(), UiAction.ExternalDisconnect.INSTANCE);

        assertThat(result.state()).isEqualTo(UiConnectionState.disconnected());
        assertThat(result.sideEffects()).isEmpty();
    }

    @Test
    void terminalAuthFailureWhileConnectedFailsTheConnectionSoConnectReconnects() {
        UiStateReducer.Reduction result = reducer.reduce(connected(), UiAction.AuthTerminallyFailed.INSTANCE);

        assertThat(result.state()).isInstanceOf(UiConnectionState.Failed.class);
        assertThat(result.state().status().primaryButtonLabel()).isEqualTo("Connect");
        assertThat(result.state().status().message())
                .startsWith("OAuth failed: token refresh rejected")
                .contains("Connect to sign in again");
        assertThat(result.sideEffects()).containsExactly(
                UiSideEffect.ReleaseConnectionAuth.INSTANCE,
                UiSideEffect.RetireConnection.INSTANCE,
                UiSideEffect.RunDisconnect.INSTANCE,
                UiSideEffect.ClearOAuthStrategy.INSTANCE,
                UiSideEffect.ClearInventory.INSTANCE);
    }

    @Test
    void terminalAuthFailureOutsideConnectedIsIgnored() {
        UiStateReducer.Reduction result = reducer.reduce(
                UiConnectionState.disconnected(), UiAction.AuthTerminallyFailed.INSTANCE);

        assertThat(result.state()).isEqualTo(UiConnectionState.disconnected());
    }

    @Test
    void cancellingAConnectReportsCancelled() {
        UiStateReducer.Reduction result = reducer.reduce(connecting(), UiAction.DisconnectRequested.INSTANCE);

        assertThat(result.state()).isInstanceOf(UiConnectionState.Disconnected.class);
        assertThat(result.state().status().message()).isEqualTo("Connection cancelled");
    }

    @Test
    void serverSideDisconnectReportsConnectionLost() {
        UiStateReducer.Reduction result = reducer.reduce(connected(), UiAction.ExternalDisconnect.INSTANCE);

        assertThat(result.state().status().state()).isEqualTo(ConnectionState.DISCONNECTED);
        assertThat(result.state().status().message())
                .startsWith("Connection lost")
                .contains("Connect again");
    }

    @Test
    void userDisconnectStillReportsPlainDisconnected() {
        UiStateReducer.Reduction result = reducer.reduce(connected(), UiAction.DisconnectRequested.INSTANCE);

        assertThat(result.state()).isEqualTo(UiConnectionState.disconnected());
        assertThat(result.state().status().message()).isEqualTo("Disconnected");
    }

    @Test
    void disconnectNotificationAfterAFailureKeepsTheFailureVisible() {
        UiConnectionState failed = reducer.reduce(connected(), UiAction.AuthTerminallyFailed.INSTANCE).state();

        UiStateReducer.Reduction result = reducer.reduce(failed, UiAction.ExternalDisconnect.INSTANCE);

        assertThat(result.state()).isSameAs(failed);
        assertThat(result.sideEffects()).isEmpty();
    }

    @Test
    void connectFailurePointsAtTheLog() {
        UiStateReducer.Reduction result = reducer.reduce(connecting(), new UiAction.ConnectFailed("HTTP 500"));

        assertThat(result.state().status().message())
                .isEqualTo("Connect failed: HTTP 500 · Open Log below for details");
    }

    @Test
    void validationRejectionDoesNotPointAtTheLog() {
        UiStateReducer.Reduction result = reducer.reduce(UiConnectionState.disconnected(),
                new UiAction.ConnectRejected(ConnectPhase.CONNECT, "Endpoint must use http or https"));

        assertThat(result.state().status().message()).isEqualTo("Connect failed: Endpoint must use http or https");
    }

    @Test
    void connectRejectedWhileConnectedTearsDownTheLiveSession() {
        UiStateReducer.Reduction result = reducer.reduce(connected(),
                new UiAction.ConnectRejected(ConnectPhase.CONNECT, "endpoint URL is required"));

        assertThat(result.state()).isInstanceOf(UiConnectionState.Failed.class);
        assertThat(result.sideEffects()).contains(
                UiSideEffect.RunDisconnect.INSTANCE,
                UiSideEffect.ClearOAuthStrategy.INSTANCE,
                UiSideEffect.ClearInventory.INSTANCE);
    }

    @Test
    void enteringATerminalStateReleasesConnectionAuthAndRetiresTheConnectionFirst() {
        UiStateReducer.Reduction result = reducer.reduce(connecting(), new UiAction.ConnectFailed("HTTP 500"));

        assertThat(result.sideEffects()).startsWith(
                UiSideEffect.ReleaseConnectionAuth.INSTANCE,
                UiSideEffect.RetireConnection.INSTANCE);
    }

    @Test
    void acceptedConnectRequestReleasesThePreviousConnectionAuthOnly() {
        UiStateReducer.Reduction result = reducer.reduce(connected(), new UiAction.ConnectRequested(sampleAttempt()));

        assertThat(result.sideEffects()).containsExactly(
                UiSideEffect.ReleaseConnectionAuth.INSTANCE,
                new UiSideEffect.SpawnConnectWorker(sampleAttempt()));
    }

    @Test
    void stayingInATerminalStateEmitsNoLifecycleEffects() {
        UiStateReducer.Reduction result = reducer.reduce(
                UiConnectionState.disconnected(), UiAction.ExternalDisconnect.INSTANCE);

        assertThat(result.sideEffects()).isEmpty();
    }

    private static ConnectAttempt sampleAttempt() {
        return ConnectAttempt.withSnapshot(
                "https://mcp.example.com/mcp", TransportType.STREAMABLE_HTTP, "mcp.example.com",
                "None", null);
    }

    private static ConnectResult sampleResult(int toolCount) {
        List<McpToolDefinition> tools = new java.util.ArrayList<>();
        for (int i = 0; i < toolCount; i++) {
            tools.add(new McpToolDefinition("tool" + i, "desc", "{}"));
        }
        return new ConnectResult(
                tools,
                List.<McpResourceDefinition>of(),
                List.<McpResourceTemplateDefinition>of(),
                List.<McpPromptDefinition>of(),
                ServerMetadata.empty());
    }

    private static UiConnectionState connecting() {
        return UiConnectionState.connecting(ConnectAttempt.withSnapshot("https://mcp.example.com/mcp",
                TransportType.AUTO, "mcp.example.com", "None", null), Cancellable.NOOP, ConnectPhase.CONNECT, "…");
    }

    private static UiConnectionState connected() {
        ConnectResult result = new ConnectResult(List.of(), List.of(), List.of(), List.of(), ServerMetadata.empty());
        return UiConnectionState.connected(result, "mcp.example.com",
                ConnectionStatus.connected(null, "mcp.example.com", null, 0), CONNECTED_AS);
    }
}
