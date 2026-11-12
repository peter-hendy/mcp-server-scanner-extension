package com.mcpscanner.ui;

import com.mcpscanner.scan.ScanMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ScanModeTextTest {

    @Test
    void eachModeHasAMenuLabel() {
        assertThat(ScanModeText.of(ScanMode.MCP_ONLY).menuLabel()).isEqualTo("Scan with extension (recommended)");
        assertThat(ScanModeText.of(ScanMode.MCP_AND_BURP).menuLabel()).isEqualTo("Scan with Burp Scanner");
    }

    @Test
    void eachModeHasALabelThatCompletesScanWith() {
        assertThat(ScanModeText.of(ScanMode.MCP_ONLY).label()).isEqualTo("the extension's checks");
        assertThat(ScanModeText.of(ScanMode.MCP_AND_BURP).label())
                .isEqualTo("Burp Scanner (extension and built-in checks)");
    }

    @Test
    void eachModeHasAShortLabelForTheScanBar() {
        assertThat(ScanModeText.of(ScanMode.MCP_ONLY).shortLabel()).isEqualTo("Extension");
        assertThat(ScanModeText.of(ScanMode.MCP_AND_BURP).shortLabel()).isEqualTo("Burp Scanner");
    }

    @Test
    void eachModeDescribesTheChecksItRuns() {
        assertThat(ScanModeText.of(ScanMode.MCP_ONLY).checksRun(7)).isEqualTo("7 extension checks only");
        assertThat(ScanModeText.of(ScanMode.MCP_AND_BURP).checksRun(7))
                .isEqualTo("7 extension checks and Burp's built-in active checks");
    }

    @Test
    void eachModeSaysWhereItsResultsAppear() {
        assertThat(ScanModeText.of(ScanMode.MCP_ONLY).resultsLocation())
                .isEqualTo("Target › Issues, progress in the Logger");
        assertThat(ScanModeText.of(ScanMode.MCP_AND_BURP).resultsLocation()).isEqualTo("Burp's Dashboard");
    }

    @Test
    void eachModeNamesWhatInvokesTheTools() {
        assertThat(ScanModeText.of(ScanMode.MCP_ONLY).invoker()).isEqualTo("The extension's checks");
        assertThat(ScanModeText.of(ScanMode.MCP_AND_BURP).invoker())
                .isEqualTo("Burp Scanner (the extension's checks and Burp's built-in active checks)");
    }

    @ParameterizedTest
    @EnumSource(ScanMode.class)
    void everyModeHasText(ScanMode mode) {
        assertThat(ScanModeText.of(mode)).isNotNull();
    }
}
