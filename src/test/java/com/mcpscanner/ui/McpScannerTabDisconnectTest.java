package com.mcpscanner.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static com.mcpscanner.ui.McpScannerTabFixture.awaitConnectLabel;
import static com.mcpscanner.ui.McpScannerTabFixture.click;
import static com.mcpscanner.ui.McpScannerTabFixture.connectButton;
import static com.mcpscanner.ui.McpScannerTabFixture.emptyInventory;
import static com.mcpscanner.ui.McpScannerTabFixture.fromEdt;
import static com.mcpscanner.ui.McpScannerTabFixture.onEdt;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class McpScannerTabDisconnectTest {

    private McpScannerTabFixture fixture;
    private final CompletableFuture<Void> disconnecting = new CompletableFuture<>();

    @BeforeEach
    void setUp() {
        fixture = new McpScannerTabFixture();
    }

    @Test
    void connectIsDisabledWithAReasonWhileTheDisconnectIsStillRunning() throws Exception {
        McpScannerTab tab = disconnectingTab();

        assertThat(fromEdt(() -> connectButton(tab).isEnabled())).isFalse();
        assertThat(fromEdt(() -> connectButton(tab).getToolTipText())).contains("Disconnecting…");
    }

    @Test
    void aConnectRequestedWhileDisconnectingIsRefused() throws Exception {
        McpScannerTab tab = disconnectingTab();

        onEdt(() -> click(connectButton(tab)));
        disconnecting.complete(null);

        await().atMost(ofSeconds(5)).until(() -> fromEdt(() -> connectButton(tab).isEnabled()));
        verify(fixture.clientManager, times(1)).connect(any());
        assertThat(fromEdt(() -> connectButton(tab).getText())).isEqualTo("Connect");
    }

    @Test
    void connectWorksAgainOnceTheDisconnectFinishes() throws Exception {
        McpScannerTab tab = disconnectingTab();
        disconnecting.complete(null);
        await().atMost(ofSeconds(5)).until(() -> fromEdt(() -> connectButton(tab).isEnabled()));

        onEdt(() -> click(connectButton(tab)));

        awaitConnectLabel(tab, "Disconnect");
        verify(fixture.clientManager, times(2)).connect(any());
    }

    private McpScannerTab disconnectingTab() throws Exception {
        McpScannerTab tab = fixture.connected(emptyInventory());
        doAnswer(call -> disconnecting.get()).when(fixture.clientManager).disconnect();
        onEdt(() -> click(connectButton(tab)));
        awaitConnectLabel(tab, "Connect");
        return tab;
    }
}
