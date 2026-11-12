package com.mcpscanner.ui.state;

import com.mcpscanner.client.TransportType;
import com.mcpscanner.scan.ScanOutcome;
import com.mcpscanner.core.oauth.RegistrationMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionStatusTest {

    @Test
    void disconnectedStatusHasGrayDotAndDefaultText() {
        ConnectionStatus status = ConnectionStatus.disconnected();

        assertThat(status.state()).isEqualTo(ConnectionState.DISCONNECTED);
        assertThat(status.message()).isEqualTo("Disconnected");
    }

    @Test
    void connectingStatusCarriesProgressMessage() {
        ConnectionStatus status = ConnectionStatus.connecting("OAuth: awaiting browser callback");

        assertThat(status.state()).isEqualTo(ConnectionState.CONNECTING);
        assertThat(status.message()).contains("browser callback");
    }

    @Test
    void connectedStatusFormatsSubjectExpiryAndToolCount() {
        ConnectionStatus status = ConnectionStatus.connected(
                "user@example.com",
                "mcp.example.com",
                42L,
                30);

        assertThat(status.state()).isEqualTo(ConnectionState.CONNECTED);
        assertThat(status.message()).isEqualTo(
                "Connected as user@example.com to mcp.example.com · expires in 42 min · 30 tools");
    }

    @Test
    void connectedStatusOmitsSubjectAndExpiryWhenAbsent() {
        ConnectionStatus status = ConnectionStatus.connected(null, "mcp.example.com", null, 5);

        assertThat(status.message()).isEqualTo("Connected to mcp.example.com · 5 tools");
    }

    @Test
    void connectedStatusShowsResolvedTransportAndProtocolVersion() {
        ConnectionStatus status = ConnectionStatus.connected(null, "mcp.example.com", null, 5)
                .withNegotiatedProtocol(TransportType.MODERN_HTTP, "2025-11-25");

        assertThat(status.message()).isEqualTo(
                "Connected to mcp.example.com · 5 tools · HTTP 2026-07-28 · MCP 2025-11-25");
    }

    @Test
    void connectedStatusShowsResolvedOAuthClientRoute() {
        ConnectionStatus status = ConnectionStatus.connected(null, "mcp.example.com", null, 5)
                .withRegistrationMode(RegistrationMode.CIMD);

        assertThat(status.message()).contains("OAuth client: CIMD").doesNotContain("Auto");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "OAuth 2.1", "Client Credentials", "Enterprise Managed", "Bearer Token", "Custom Headers", "None"})
    void connectedStatusShowsSelectedAuthenticationMethod(String authMethod) {
        ConnectionStatus status = ConnectionStatus.connected(null, "mcp.example.com", null, 5)
                .withAuthMethod(authMethod);

        assertThat(status.message()).contains("Auth: " + authMethod);
    }

    @Test
    void failedStatusContainsPhaseAndReason() {
        ConnectionStatus status = ConnectionStatus.failed(
                ConnectPhase.OAUTH, "empty scope options are not supported for issuer https://x");

        assertThat(status.state()).isEqualTo(ConnectionState.FAILED);
        assertThat(status.message()).isEqualTo(
                "OAuth failed: empty scope options are not supported for issuer https://x");
    }

    @Test
    void scanStartedNamesWhatWasScannedAndWhereResultsAppear() {
        ConnectionStatus s = ConnectionStatus.scanStarted("3 items (2 tools, 1 resource)", "the results tab");
        assertThat(s.message()).isEqualTo("Scan started: 3 items (2 tools, 1 resource) · Results in the results tab");
        assertThat(s.state()).isEqualTo(ConnectionState.CONNECTED);
        assertThat(s.primaryButtonLabel()).isEqualTo("Disconnect");
    }

    @Test
    void scanFinishedCountsIssuesAndSaysWhereTheyAre() {
        ConnectionStatus s = ConnectionStatus.scanEnded(new ScanOutcome(11, false), "Target › Issues");
        assertThat(s.message()).isEqualTo("Scan finished · 11 issues · Results in Target › Issues");
        assertThat(s.state()).isEqualTo(ConnectionState.CONNECTED);
    }

    @Test
    void scanFinishedWithCollaboratorPayloadsWatchedSaysLateIssuesWillFollow() {
        ConnectionStatus s = ConnectionStatus.scanEnded(new ScanOutcome(2, false, 14), "Target › Issues");
        assertThat(s.message()).isEqualTo("Scan finished · 2 issues · Results in Target › Issues · 14 Collaborator "
                + "payloads still being watched for up to 15 min; late issues will be added to the site map");
    }

    @Test
    void stoppedScanSaysItWasCancelledAndCountsASingleIssue() {
        ConnectionStatus s = ConnectionStatus.scanEnded(new ScanOutcome(1, true), "Target › Issues");
        assertThat(s.message()).isEqualTo("Scan cancelled · 1 issue · Results in Target › Issues");
    }

    @Test
    void scanFailedFactoryPreservesConnectedState() {
        ConnectionStatus s = ConnectionStatus.scanFailed("not connected");
        assertThat(s.state()).isEqualTo(ConnectionState.CONNECTED);
        assertThat(s.message()).isEqualTo("Scan failed: not connected");
        assertThat(s.primaryButtonLabel()).isEqualTo("Disconnect");
    }

    @Test
    void buttonLabelMatchesState() {
        assertThat(ConnectionStatus.disconnected().primaryButtonLabel()).isEqualTo("Connect");
        assertThat(ConnectionStatus.connecting("…").primaryButtonLabel()).isEqualTo("Cancel");
        assertThat(ConnectionStatus.connected(null, "h", null, 0).primaryButtonLabel())
                .isEqualTo("Disconnect");
        assertThat(ConnectionStatus.failed(ConnectPhase.OAUTH, "y").primaryButtonLabel()).isEqualTo("Connect");
    }

    @Test
    void connectingPrimaryButtonLabelIsCancel() {
        assertThat(ConnectionStatus.connecting("OAuth: opening browser…").primaryButtonLabel())
                .isEqualTo("Cancel");
    }
}
