package com.mcpscanner.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScanAllJsonRoundTripTest {

    private final ObjectMapper harnessJson = new ObjectMapper();

    @Test
    void theHarnessReadsALaunchedScanWithAndWithoutACatalogLimit() throws Exception {
        assertThat(harnessJson.readValue("{\"requests\":51,\"catalogLimit\":\"tools 124 -> 50\"}", LaunchedScan.class))
                .isEqualTo(new LaunchedScan(51, "tools 124 -> 50"));
        assertThat(harnessJson.readValue("{\"requests\":19}", LaunchedScan.class)).isEqualTo(new LaunchedScan(19));
    }

    @Test
    void aScanAllCommandKeepsItsCatalogLimitAcrossTheWire() throws Exception {
        ScanAllCommand sent = new ScanAllCommand("http://127.0.0.1:1/mcp", null, null, null, null, null, true, null, 100);

        ScanAllCommand received = harnessJson.readValue(harnessJson.writeValueAsString(sent), ScanAllCommand.class);

        assertThat(received.maxCatalogItems()).isEqualTo(100);
        assertThat(received.divertBuiltInChecks()).isTrue();
    }
}
