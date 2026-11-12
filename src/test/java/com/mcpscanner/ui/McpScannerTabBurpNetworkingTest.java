package com.mcpscanner.ui;

import com.mcpscanner.config.AuthTypeNames;
import com.mcpscanner.client.BurpStreamingResponseException;
import com.mcpscanner.logging.McpEventLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mcpscanner.ui.McpScannerTabFixture.ENDPOINT;
import static com.mcpscanner.ui.McpScannerTabFixture.awaitConnectLabel;
import static com.mcpscanner.ui.McpScannerTabFixture.click;
import static com.mcpscanner.ui.McpScannerTabFixture.connectButton;
import static com.mcpscanner.ui.McpScannerTabFixture.emptyInventory;
import static com.mcpscanner.ui.McpScannerTabFixture.endpointField;
import static com.mcpscanner.ui.McpScannerTabFixture.fromEdt;
import static com.mcpscanner.ui.McpScannerTabFixture.onEdt;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpScannerTabBurpNetworkingTest {

    private static final String SETTING_KEY = "mcp.networking.use_burp";

    private final McpScannerTabFixture fixture = new McpScannerTabFixture();
    private final AtomicInteger prompts = new AtomicInteger();

    @AfterEach
    void shutDown() {
        fixture.eventLog.shutdown();
    }

    @Test
    void acceptingThePromptTurnsBurpNetworkingOffAndReconnects() throws Exception {
        answerPrompt(true);
        when(fixture.clientManager.connect(any())).thenThrow(burpRefusedStream()).thenReturn(emptyInventory());

        McpScannerTab tab = clickConnect();

        awaitConnectLabel(tab, "Disconnect");
        assertThat(prompts).hasValue(1);
        verify(fixture.store).setBoolean(SETTING_KEY, false);
        verify(fixture.clientManager, times(2)).connect(any());
    }

    @Test
    void decliningThePromptLeavesBurpNetworkingOnAndTheConnectionFailed() throws Exception {
        answerPrompt(false);
        when(fixture.clientManager.connect(any())).thenThrow(burpRefusedStream());

        clickConnect();

        await().atMost(ofSeconds(5)).until(() -> prompts.get() == 1);
        verify(fixture.store, never()).setBoolean(eq(SETTING_KEY), anyBoolean());
        verify(fixture.clientManager, times(1)).connect(any());
        assertThat(loggedErrors()).anyMatch(message -> message.contains("Burp doesn't pass streamed replies"));
    }

    @Test
    void otherConnectFailuresDoNotPrompt() throws Exception {
        answerPrompt(true);
        when(fixture.clientManager.connect(any())).thenThrow(new IllegalStateException("refused"));

        clickConnect();

        await().atMost(ofSeconds(5)).until(() -> loggedErrors().stream().anyMatch(m -> m.contains("refused")));
        assertThat(prompts).hasValue(0);
    }

    private void answerPrompt(boolean turnOff) {
        fixture.burpNetworkingPrompt = parent -> {
            prompts.incrementAndGet();
            return turnOff;
        };
    }

    private McpScannerTab clickConnect() throws Exception {
        McpScannerTab tab = fromEdt(fixture::newTab);
        onEdt(() -> {
            endpointField(tab).setText(ENDPOINT);
            fixture.useAuth(AuthTypeNames.NONE);
            click(connectButton(tab));
        });
        return tab;
    }

    private java.util.List<String> loggedErrors() {
        return fixture.eventLog.snapshot().stream()
                .filter(entry -> entry.level() == McpEventLog.Level.ERROR)
                .map(McpEventLog.LogEntry::message)
                .toList();
    }

    private static IllegalStateException burpRefusedStream() {
        return new IllegalStateException("Failed to establish scanner session",
                new BurpStreamingResponseException(URI.create(ENDPOINT)));
    }
}
