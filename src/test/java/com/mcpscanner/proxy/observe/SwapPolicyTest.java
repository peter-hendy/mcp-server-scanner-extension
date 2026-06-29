package com.mcpscanner.proxy.observe;

import burp.api.montoya.core.ToolSource;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SwapPolicyTest {

    private final HttpRequestToBeSent request = mock(HttpRequestToBeSent.class);

    @Test
    void swapAllMatchingToolsAlwaysSwaps() {
        SwapPolicy policy = new SwapAllMatchingTools();

        assertThat(policy.shouldSwap(request)).isTrue();
    }

    @Test
    void swapAllMatchingToolsNeverReadsToolSource() {
        SwapPolicy policy = new SwapAllMatchingTools();

        policy.shouldSwap(request);

        verify(request, never()).toolSource();
    }

    @ParameterizedTest
    @EnumSource(value = ToolType.class, names = {"REPEATER", "SCANNER", "INTRUDER"})
    void scannerFamilySwapsForScannerFamilyTools(ToolType toolType) {
        SwapPolicy policy = new ScannerFamilySwapPolicy();
        stubToolSource(toolTypeMatches(toolType));

        assertThat(policy.shouldSwap(request)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ToolType.class, names = {"PROXY", "EXTENSIONS"})
    void scannerFamilyDoesNotSwapForNonScannerFamilyTools(ToolType toolType) {
        SwapPolicy policy = new ScannerFamilySwapPolicy();
        stubToolSource(toolTypeMatches(toolType));

        assertThat(policy.shouldSwap(request)).isFalse();
    }

    private void stubToolSource(boolean isFromScannerFamily) {
        ToolSource toolSource = mock(ToolSource.class);
        when(toolSource.isFromTool(ToolType.REPEATER, ToolType.SCANNER, ToolType.INTRUDER))
                .thenReturn(isFromScannerFamily);
        when(request.toolSource()).thenReturn(toolSource);
    }

    private static boolean toolTypeMatches(ToolType toolType) {
        return toolType == ToolType.REPEATER
                || toolType == ToolType.SCANNER
                || toolType == ToolType.INTRUDER;
    }
}
