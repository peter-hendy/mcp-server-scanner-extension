package com.mcpscanner.ui.serverinfo;

import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedProtocolVersionsTest {

    @Test
    void theNewestIsNoOlderThanAnyVersionTheExtensionSpeaks() {
        String newest = SupportedProtocolVersions.newest();

        assertThat(newest).isGreaterThanOrEqualTo(McpRequests.MODERN_VERSION);
        assertThat(McpRequests.supportedInitializeVersions()).allSatisfy(version ->
                assertThat(newest).isGreaterThanOrEqualTo(version));
        assertThat(newest.equals(McpRequests.MODERN_VERSION)
                || McpRequests.supportedInitializeVersions().contains(newest)).isTrue();
    }

    @Test
    void onlyTheNewestCountsAsLatest() {
        assertThat(SupportedProtocolVersions.isLatest(SupportedProtocolVersions.newest())).isTrue();
        assertThat(SupportedProtocolVersions.isLatest("2024-11-05")).isFalse();
        assertThat(SupportedProtocolVersions.isLatest(null)).isFalse();
    }
}
