package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.client.TransportType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class McpExchangeLogTest {

    private final McpExchangeLog log = new McpExchangeLog();

    @Test
    void clientToServerObservationAppendsOneExchangeRow() {
        log.observe(clientToServer("session-1", "7", "tools/call"));

        List<McpExchange> exchanges = log.exchanges();
        assertThat(exchanges).hasSize(1);
        McpExchange row = exchanges.get(0);
        assertThat(row.direction()).isEqualTo(Direction.CLIENT_TO_SERVER);
        assertThat(row.method()).isEqualTo("tools/call");
        assertThat(row.sessionId()).isEqualTo("session-1");
        assertThat(row.jsonrpcId()).isEqualTo("7");
        assertThat(row.exposureSurface()).isEqualTo(ExposureSurface.LIVE_RUNTIME_OUTPUT);
        assertThat(row.generation()).isZero();
        assertThat(row.status()).isNull();
        assertThat(row.decodedResponse()).isNull();
        assertThat(row.timing()).isNotNull();
    }

    @Test
    void serverToClientObservationIsIgnoredForNow() {
        log.observe(serverToClient("session-1", "7", "tools/call"));

        assertThat(log.exchanges()).isEmpty();
    }

    @Test
    void exchangesAccessorReturnsDefensiveCopy() {
        log.observe(clientToServer("session-1", "1", "tools/call"));

        List<McpExchange> first = log.exchanges();
        log.observe(clientToServer("session-1", "2", "resources/read"));

        assertThat(first).hasSize(1);
        assertThat(log.exchanges()).hasSize(2);
    }

    @Test
    void concurrentObservationsDoNotLoseRowsOrThrow() {
        int count = 2_000;

        IntStream.range(0, count).parallel().forEach(i ->
                log.observe(clientToServer("session-" + (i % 8), Integer.toString(i), "tools/call")));

        assertThat(log.exchanges()).hasSize(count);
    }

    private static ObservedMessage clientToServer(String sessionId, String jsonrpcId, String method) {
        return observed(Direction.CLIENT_TO_SERVER, sessionId, jsonrpcId, method);
    }

    private static ObservedMessage serverToClient(String sessionId, String jsonrpcId, String method) {
        return observed(Direction.SERVER_TO_CLIENT, sessionId, jsonrpcId, method);
    }

    private static ObservedMessage observed(Direction direction, String sessionId, String jsonrpcId, String method) {
        return new ObservedMessage(direction, TransportType.STREAMABLE_HTTP, sessionId, jsonrpcId, method,
                mock(HttpRequest.class), mock(JsonNode.class), null);
    }
}
