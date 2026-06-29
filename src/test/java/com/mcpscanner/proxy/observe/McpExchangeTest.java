package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.client.TransportType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class McpExchangeTest {

    private final HttpRequest request = mock(HttpRequest.class);
    private final JsonNode decodedResponse = mock(JsonNode.class);
    private final Instant timing = Instant.parse("2026-06-29T00:00:00Z");

    @Test
    void accessorsReturnConstructorValues() {
        McpExchange exchange = new McpExchange(
                "session-1",
                TransportType.STREAMABLE_HTTP,
                Direction.CLIENT_TO_SERVER,
                "rpc-1",
                3,
                "tools/call",
                request,
                decodedResponse,
                200,
                timing,
                ExposureSurface.LIVE_RUNTIME_OUTPUT);

        assertThat(exchange.sessionId()).isEqualTo("session-1");
        assertThat(exchange.transport()).isEqualTo(TransportType.STREAMABLE_HTTP);
        assertThat(exchange.direction()).isEqualTo(Direction.CLIENT_TO_SERVER);
        assertThat(exchange.jsonrpcId()).isEqualTo("rpc-1");
        assertThat(exchange.generation()).isEqualTo(3);
        assertThat(exchange.method()).isEqualTo("tools/call");
        assertThat(exchange.request()).isSameAs(request);
        assertThat(exchange.decodedResponse()).isSameAs(decodedResponse);
        assertThat(exchange.status()).isEqualTo(200);
        assertThat(exchange.timing()).isEqualTo(timing);
        assertThat(exchange.exposureSurface()).isEqualTo(ExposureSurface.LIVE_RUNTIME_OUTPUT);
    }

    @Test
    void statusCanBeNullUntilResponseIsCorrelated() {
        McpExchange exchange = new McpExchange(
                "session-1",
                TransportType.SSE,
                Direction.CLIENT_TO_SERVER,
                "rpc-1",
                0,
                "tools/call",
                request,
                null,
                null,
                timing,
                ExposureSurface.LIVE_RUNTIME_OUTPUT);

        assertThat(exchange.status()).isNull();
    }

    @Test
    void linkReturnsLinkKeyOfSessionIdJsonrpcIdAndGeneration() {
        McpExchange exchange = new McpExchange(
                "session-1",
                TransportType.STREAMABLE_HTTP,
                Direction.CLIENT_TO_SERVER,
                "rpc-1",
                7,
                "tools/call",
                request,
                decodedResponse,
                200,
                timing,
                ExposureSurface.DISCOVERY_METADATA);

        assertThat(exchange.link()).isEqualTo(new LinkKey("session-1", "rpc-1", 7));
    }

    @Test
    void exchangesDifferingOnlyByDirectionAreDistinctButShareLinkKey() {
        McpExchange clientToServer = new McpExchange(
                "session-1",
                TransportType.STREAMABLE_HTTP,
                Direction.CLIENT_TO_SERVER,
                "rpc-1",
                2,
                "tools/call",
                request,
                decodedResponse,
                200,
                timing,
                ExposureSurface.LIVE_RUNTIME_OUTPUT);
        McpExchange serverToClient = new McpExchange(
                "session-1",
                TransportType.STREAMABLE_HTTP,
                Direction.SERVER_TO_CLIENT,
                "rpc-1",
                2,
                "tools/call",
                request,
                decodedResponse,
                200,
                timing,
                ExposureSurface.LIVE_RUNTIME_OUTPUT);

        assertThat(clientToServer).isNotEqualTo(serverToClient);
        assertThat(clientToServer.link()).isEqualTo(serverToClient.link());
    }
}
