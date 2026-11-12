package com.mcpscanner.ui.completion;

import com.mcpscanner.client.CompletionOutcome;
import com.mcpscanner.client.CompletionReference;
import com.mcpscanner.client.CompletionRequest;
import com.mcpscanner.client.McpClientManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientCompletionGatewayTest {

    private final McpClientManager clientManager = mock(McpClientManager.class);

    @Test
    void isDisconnectedUnlessBothTheUiAndTheClientAreConnected() {
        when(clientManager.isConnected()).thenReturn(true);
        when(clientManager.completionsAdvertised()).thenReturn(true);

        assertThat(new ClientCompletionGateway(clientManager, () -> false).availability())
                .isEqualTo(CompletionAvailability.DISCONNECTED);

        when(clientManager.isConnected()).thenReturn(false);
        assertThat(new ClientCompletionGateway(clientManager, () -> true).availability())
                .isEqualTo(CompletionAvailability.DISCONNECTED);
    }

    @Test
    void followsTheAdvertisedCapabilityWhileConnected() {
        when(clientManager.isConnected()).thenReturn(true);
        ClientCompletionGateway gateway = new ClientCompletionGateway(clientManager, () -> true);

        when(clientManager.completionsAdvertised()).thenReturn(false);
        assertThat(gateway.availability()).isEqualTo(CompletionAvailability.NOT_ADVERTISED);

        when(clientManager.completionsAdvertised()).thenReturn(true);
        assertThat(gateway.availability()).isEqualTo(CompletionAvailability.AVAILABLE);
    }

    @Test
    void completesThroughTheClient() {
        CompletionRequest request = new CompletionRequest(new CompletionReference.Prompt("p"), "a", "");
        CompletionOutcome values = new CompletionOutcome.Values(List.of("x"), null, null);
        when(clientManager.complete(request)).thenReturn(values);

        assertThat(new ClientCompletionGateway(clientManager, () -> true).complete(request)).isEqualTo(values);
    }
}
