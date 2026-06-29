package com.mcpscanner.proxy.observe;

import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToggleAwareSwapPolicyTest {

    private final HttpRequestToBeSent request = mock(HttpRequestToBeSent.class);

    @Test
    void offDelegatesToSwapAllMatchingToolsAndAlwaysSwaps() {
        SwapPolicy policy = new ToggleAwareSwapPolicy(() -> false);

        assertThat(policy.shouldSwap(request)).isTrue();
    }

    @Test
    void offNeverReadsToolSourceSoItStaysByteIdenticalToTheLegacyHandler() {
        SwapPolicy policy = new ToggleAwareSwapPolicy(() -> false);

        policy.shouldSwap(request);

        verify(request, never()).toolSource();
    }

    @Test
    void onNarrowsToScannerFamily() {
        SwapPolicy policy = new ToggleAwareSwapPolicy(() -> true);
        stubScannerFamily(true);

        assertThat(policy.shouldSwap(request)).isTrue();
    }

    @Test
    void onDoesNotSwapNonScannerFamilyTraffic() {
        SwapPolicy policy = new ToggleAwareSwapPolicy(() -> true);
        stubScannerFamily(false);

        assertThat(policy.shouldSwap(request)).isFalse();
    }

    @Test
    void readsTheSupplierLiveOnEachCall() {
        AtomicBoolean enabled = new AtomicBoolean(false);
        SwapPolicy policy = new ToggleAwareSwapPolicy(enabled::get);

        assertThat(policy.shouldSwap(request)).isTrue();

        enabled.set(true);
        stubScannerFamily(false);
        assertThat(policy.shouldSwap(request)).isFalse();
    }

    private void stubScannerFamily(boolean isFromScannerFamily) {
        ToolSource toolSource = mock(ToolSource.class);
        when(toolSource.isFromTool(ToolType.REPEATER, ToolType.SCANNER, ToolType.INTRUDER))
                .thenReturn(isFromScannerFamily);
        when(request.toolSource()).thenReturn(toolSource);
    }
}
