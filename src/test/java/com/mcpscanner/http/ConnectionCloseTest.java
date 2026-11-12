package com.mcpscanner.http;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionCloseTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void replacesAnyConnectionHeaderOnARequestWithClose() {
        HttpRequest request = HttpRequest.httpRequestFromUrl("https://mcp.example.com/mcp")
                .withAddedHeader("connection", "keep-alive")
                .withAddedHeader("Mcp-Session-Id", "abc");

        HttpRequest closing = ConnectionClose.on(request);

        assertThat(closing.headerValue("Connection")).isEqualTo("close");
        assertThat(closing.headerValue("connection")).isNull();
        assertThat(closing.headerValue("Mcp-Session-Id")).isEqualTo("abc");
    }

    @Test
    void dropsAnyConnectionHeaderFromAHeaderMap() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("CONNECTION", List.of("keep-alive"));
        headers.put("Accept", List.of("application/json"));

        assertThat(ConnectionClose.without(headers)).containsExactly(Map.entry("Accept", List.of("application/json")));
    }

    @Test
    void leavesTheCallersHeaderMapUntouched() {
        Map<String, List<String>> headers = Map.of("Connection", List.of("close"));

        ConnectionClose.without(headers);

        assertThat(headers).containsOnlyKeys("Connection");
    }

    @Test
    void removesOnlyAConnectionCloseItAddedItself() {
        HttpRequest closing = ConnectionClose.on(HttpRequest.httpRequestFromUrl("https://mcp.example.com/mcp"));
        HttpRequest upgrading = closing.withHeader("Connection", "Upgrade");

        assertThat(ConnectionClose.withoutOurs(closing).hasHeader("Connection")).isFalse();
        assertThat(ConnectionClose.withoutOurs(upgrading).headerValue("Connection")).isEqualTo("Upgrade");
    }
}
