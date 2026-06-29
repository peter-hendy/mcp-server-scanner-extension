package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.client.TransportType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class NoopPassiveLiveRunnerTest {

    private final PassiveLiveRunner runner = new NoopPassiveLiveRunner();

    @Test
    void scanAcceptsFullyPopulatedExchangeWithoutThrowing() {
        McpExchange exchange = new McpExchange(
                "session-1",
                TransportType.STREAMABLE_HTTP,
                Direction.SERVER_TO_CLIENT,
                "rpc-1",
                2,
                "tools/call",
                mock(HttpRequest.class),
                mock(JsonNode.class),
                200,
                Instant.parse("2026-06-29T00:00:00Z"),
                ExposureSurface.LIVE_RUNTIME_OUTPUT);

        assertThatCode(() -> runner.scan(exchange)).doesNotThrowAnyException();
    }

    @Test
    void scanAcceptsNullHeavyExchangeWithoutThrowing() {
        McpExchange exchange = new McpExchange(
                null,
                TransportType.SSE,
                Direction.SERVER_TO_CLIENT,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                ExposureSurface.LIVE_RUNTIME_OUTPUT);

        assertThatCode(() -> runner.scan(exchange)).doesNotThrowAnyException();
    }
}
