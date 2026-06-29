package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.client.TransportType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class McpExchangeLogTest {

    private final CapturingPassiveRunner passiveRunner = new CapturingPassiveRunner();
    private final McpExchangeLog log = new McpExchangeLog(passiveRunner);

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
    void correlatedResponseAppendsLinkedRowAndEvictsPendingRequest() {
        JsonNode responseNode = mock(JsonNode.class);

        log.observe(clientToServer("s", "42", "tools/call"));
        log.observe(serverToClient("s", "42", "tools/call", 200, responseNode));

        List<McpExchange> exchanges = log.exchanges();
        assertThat(exchanges).hasSize(2);

        McpExchange request = exchanges.get(0);
        McpExchange response = exchanges.get(1);

        assertThat(request.direction()).isEqualTo(Direction.CLIENT_TO_SERVER);
        assertThat(request.status()).isNull();
        assertThat(request.decodedResponse()).isNull();

        assertThat(response.direction()).isEqualTo(Direction.SERVER_TO_CLIENT);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.decodedResponse()).isSameAs(responseNode);
        assertThat(response.exposureSurface()).isEqualTo(ExposureSurface.LIVE_RUNTIME_OUTPUT);
        assertThat(response.generation()).isZero();
        assertThat(response.timing()).isNotNull();

        assertThat(response.link()).isEqualTo(request.link());

        // The pending entry was evicted: a second response with the same id finds no pending match
        // and so is appended as a standalone server-initiated row (no eviction side effects to assert).
        log.observe(serverToClient("s", "42", "tools/call", 200, mock(JsonNode.class)));
        assertThat(log.exchanges()).hasSize(3);
    }

    @Test
    void serverInitiatedResponseWithNoPendingRequestIsAppendedStandalone() {
        log.observe(clientToServer("s", "1", "tools/call"));

        log.observe(serverToClient("s", "99", "tools/call", 200, mock(JsonNode.class)));

        List<McpExchange> exchanges = log.exchanges();
        assertThat(exchanges).hasSize(2);

        McpExchange standalone = exchanges.get(1);
        assertThat(standalone.direction()).isEqualTo(Direction.SERVER_TO_CLIENT);
        assertThat(standalone.jsonrpcId()).isEqualTo("99");
        assertThat(standalone.status()).isEqualTo(200);

        // The unrelated pending request for id "1" is untouched: its response still correlates and links.
        McpExchange request = exchanges.get(0);
        log.observe(serverToClient("s", "1", "tools/call", 200, mock(JsonNode.class)));
        List<McpExchange> after = log.exchanges();
        assertThat(after).hasSize(3);
        assertThat(after.get(2).link()).isEqualTo(request.link());
    }

    @Test
    void responseIsLinkedNotMergedLeavingTheRequestRowUntouched() {
        log.observe(clientToServer("s", "42", "tools/call"));
        McpExchange originalRequest = log.exchanges().get(0);

        log.observe(serverToClient("s", "42", "tools/call", 200, mock(JsonNode.class)));

        List<McpExchange> exchanges = log.exchanges();
        assertThat(exchanges).hasSize(2);
        // The original request row object is still present, unchanged: two distinct rows, opposite
        // directions, same link — linked, not merged.
        assertThat(exchanges.get(0)).isSameAs(originalRequest);
        assertThat(exchanges.get(0).direction()).isEqualTo(Direction.CLIENT_TO_SERVER);
        assertThat(exchanges.get(0).status()).isNull();
        assertThat(exchanges.get(0).decodedResponse()).isNull();
        assertThat(exchanges.get(1).direction()).isEqualTo(Direction.SERVER_TO_CLIENT);
        assertThat(exchanges.get(1).link()).isEqualTo(exchanges.get(0).link());
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

    @Test
    void serverToClientObservationTriggersPassiveRunnerOnceWithTheResponseRow() {
        log.observe(serverToClient("s", "42", "tools/call", 200, mock(JsonNode.class)));

        assertThat(passiveRunner.scanned).hasSize(1);
        McpExchange scanned = passiveRunner.scanned.get(0);
        assertThat(scanned.direction()).isEqualTo(Direction.SERVER_TO_CLIENT);
        assertThat(scanned.jsonrpcId()).isEqualTo("42");
        assertThat(scanned.status()).isEqualTo(200);
        assertThat(scanned).isSameAs(log.exchanges().get(0));
    }

    @Test
    void clientToServerObservationDoesNotTriggerPassiveRunner() {
        log.observe(clientToServer("s", "42", "tools/call"));

        assertThat(passiveRunner.scanned).isEmpty();
    }

    private static ObservedMessage clientToServer(String sessionId, String jsonrpcId, String method) {
        return new ObservedMessage(Direction.CLIENT_TO_SERVER, TransportType.STREAMABLE_HTTP, sessionId, jsonrpcId,
                method, mock(HttpRequest.class), mock(JsonNode.class), null);
    }

    private static ObservedMessage serverToClient(String sessionId, String jsonrpcId, String method,
                                                  Integer status, JsonNode parsed) {
        return new ObservedMessage(Direction.SERVER_TO_CLIENT, TransportType.STREAMABLE_HTTP, sessionId, jsonrpcId,
                method, mock(HttpRequest.class), parsed, status);
    }

    private static final class CapturingPassiveRunner implements PassiveLiveRunner {
        private final List<McpExchange> scanned = new ArrayList<>();

        @Override
        public void scan(McpExchange exchange) {
            scanned.add(exchange);
        }
    }
}
