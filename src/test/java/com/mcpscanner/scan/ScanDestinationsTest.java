package com.mcpscanner.scan;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ScanDestinationsTest {

    private final ScanDestination engine = mock(ScanDestination.class);
    private final ScanDestination burpAudit = mock(ScanDestination.class);

    @Test
    void eachModeLaunchesOnItsOwnDestination() {
        ScanDestinations destinations = new ScanDestinations(
                Map.of(ScanMode.MCP_ONLY, engine, ScanMode.MCP_AND_BURP, burpAudit));

        assertThat(destinations.forMode(ScanMode.MCP_ONLY)).isSameAs(engine);
        assertThat(destinations.forMode(ScanMode.MCP_AND_BURP)).isSameAs(burpAudit);
    }

    @Test
    void aModeWithoutADestinationIsRefusedAtWiringTime() {
        assertThatThrownBy(() -> new ScanDestinations(Map.of(ScanMode.MCP_ONLY, engine)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Every scan mode needs a destination");
    }
}
