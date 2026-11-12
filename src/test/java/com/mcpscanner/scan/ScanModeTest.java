package com.mcpscanner.scan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScanModeTest {

    @Test
    void aSavedNameReadsBackAsItsMode() {
        assertThat(ScanMode.named("MCP_AND_BURP")).contains(ScanMode.MCP_AND_BURP);
    }

    @Test
    void anUnknownOrMissingNameReadsAsNoMode() {
        assertThat(ScanMode.named("FUTURE_MODE")).isEmpty();
        assertThat(ScanMode.named(null)).isEmpty();
    }
}
