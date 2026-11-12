package com.mcpscanner.checks.shared.stream;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StreamGetRequestTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void turnsAPostedMessageIntoABodilessEventStreamGetKeepingProtocolAndCredentialHeaders() {
        HttpRequest get = StreamGetRequest.from(seed(), Optional.empty());

        assertThat(get.method()).isEqualTo("GET");
        assertThat(get.bodyToString()).isEmpty();
        assertThat(get.headerValue("Accept")).isEqualTo("text/event-stream");
        assertThat(get.headerValue("MCP-Protocol-Version")).isEqualTo("2025-11-25");
        assertThat(get.headerValue("Authorization")).isEqualTo("Bearer t");
        assertThat(get.hasHeader("Content-Type")).isFalse();
        assertThat(get.hasHeader("Mcp-Method")).isFalse();
        assertThat(get.hasHeader("Mcp-Session-Id")).isFalse();
    }

    @Test
    void carriesTheGivenSessionInsteadOfTheSeeds() {
        HttpRequest get = StreamGetRequest.from(seed(), Optional.of("probe-session"));

        assertThat(get.headerValue("Mcp-Session-Id")).isEqualTo("probe-session");
    }

    private static HttpRequest seed() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json, text/event-stream");
        headers.put("MCP-Protocol-Version", "2025-11-25");
        headers.put("Mcp-Method", "tools/list");
        headers.put("Mcp-Session-Id", "live-session");
        headers.put("Authorization", "Bearer t");
        return ImmutableHttpRequest.post("http://127.0.0.1:8000/mcp", headers,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
    }
}
