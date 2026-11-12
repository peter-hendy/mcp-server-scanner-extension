package com.mcpscanner.proxy;

import com.mcpscanner.mcp.ScannerSentinels;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserRequestGuardTest {

    private final LoopbackRoutingKey key = LoopbackRoutingKey.mint();
    private final BrowserRequestGuard guard = new BrowserRequestGuard(key);

    @Test
    void refusesOriginWithoutRoutingKey() {
        assertThat(guard.refuses(Map.of("origin", "http://evil.example"))).isTrue();
    }

    @Test
    void refusesLowercaseSecFetchHeaderWithoutRoutingKey() {
        assertThat(guard.refuses(Map.of("sec-fetch-site", "cross-site"))).isTrue();
    }

    @Test
    void refusesMixedCaseSecFetchHeaderWithoutRoutingKey() {
        assertThat(guard.refuses(Map.of("Sec-Fetch-Mode", "cors"))).isTrue();
    }

    @Test
    void admitsOriginCarryingTheRoutingKey() {
        assertThat(guard.refuses(Map.of("origin", "http://evil.example",
                "x-mcp-scanner-routed", key.value()))).isFalse();
    }

    @Test
    void refusesOriginCarryingAWrongRoutingKey() {
        assertThat(guard.refuses(Map.of("origin", "http://evil.example",
                "x-mcp-scanner-routed", LoopbackRoutingKey.mint().value()))).isTrue();
    }

    @Test
    void refusesOriginCarryingOnlyAConstantSentinel() {
        assertThat(guard.refuses(Map.of("origin", "http://evil.example",
                "x-mcp-scanner-strip-auth", ScannerSentinels.SENTINEL_VALUE))).isTrue();
    }

    @Test
    void admitsRequestWithoutBrowserHeadersOrKey() {
        assertThat(guard.refuses(Map.of("content-type", "application/json"))).isFalse();
    }
}
