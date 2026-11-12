package com.mcpscanner.http;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlatHeadersTest {

    @Test
    void keepsTheFirstValueOfAnOrdinaryHeader() {
        assertThat(FlatHeaders.of(Map.of("Mcp-Session-Id", List.of("first", "second"))))
                .containsEntry("Mcp-Session-Id", "first");
    }

    @Test
    void joinsEveryAuthenticationChallengeSoNoSchemeIsLost() {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        grouped.put("www-authenticate", List.of("Basic realm=\"x\"", "Bearer resource_metadata=\"https://m\""));

        assertThat(FlatHeaders.of(grouped))
                .containsEntry("www-authenticate", "Basic realm=\"x\", Bearer resource_metadata=\"https://m\"");
    }

    @Test
    void omitsAHeaderWithoutValues() {
        assertThat(FlatHeaders.of(Map.of("X-Empty", List.of()))).isEmpty();
    }
}
