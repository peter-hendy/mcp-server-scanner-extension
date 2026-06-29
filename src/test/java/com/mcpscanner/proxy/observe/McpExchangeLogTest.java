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
    void correlatedResponseRowBorrowsItsRequestMethodAndRequestAndEvictsPendingRequest() {
        JsonNode responseNode = mock(JsonNode.class);
        ObservedMessage request = clientToServer("s", "42", "tools/call");

        log.observe(request);
        // production shape: a JSON-RPC response envelope carries no method and the row carries no request.
        log.observe(productionResponse("s", "42", 200, responseNode));

        List<McpExchange> exchanges = log.exchanges();
        assertThat(exchanges).hasSize(2);

        McpExchange requestRow = exchanges.get(0);
        McpExchange response = exchanges.get(1);

        assertThat(requestRow.direction()).isEqualTo(Direction.CLIENT_TO_SERVER);
        assertThat(requestRow.status()).isNull();
        assertThat(requestRow.decodedResponse()).isNull();

        assertThat(response.direction()).isEqualTo(Direction.SERVER_TO_CLIENT);
        assertThat(response.status()).isEqualTo(200);
        assertThat(response.decodedResponse()).isSameAs(responseNode);
        assertThat(response.exposureSurface()).isEqualTo(ExposureSurface.LIVE_RUNTIME_OUTPUT);
        assertThat(response.generation()).isZero();
        assertThat(response.timing()).isNotNull();

        // The response row borrows the originating request's method + request so the passive runner can
        // resolve the content-kind and the host from the response row alone.
        assertThat(response.method()).isEqualTo("tools/call");
        assertThat(response.request()).isSameAs(request.request());

        assertThat(response.link()).isEqualTo(requestRow.link());

        // The pending entry was evicted: a second response with the same id finds no pending match
        // and so is appended as a standalone server-initiated row with no borrowed method/request.
        log.observe(productionResponse("s", "42", 200, mock(JsonNode.class)));
        List<McpExchange> after = log.exchanges();
        assertThat(after).hasSize(3);
        assertThat(after.get(2).method()).isNull();
        assertThat(after.get(2).request()).isNull();
    }

    @Test
    void serverInitiatedResponseWithNoPendingRequestIsAppendedStandalone() {
        log.observe(clientToServer("s", "1", "tools/call"));

        log.observe(productionResponse("s", "99", 200, mock(JsonNode.class)));

        List<McpExchange> exchanges = log.exchanges();
        assertThat(exchanges).hasSize(2);

        McpExchange standalone = exchanges.get(1);
        assertThat(standalone.direction()).isEqualTo(Direction.SERVER_TO_CLIENT);
        assertThat(standalone.jsonrpcId()).isEqualTo("99");
        assertThat(standalone.status()).isEqualTo(200);
        // Uncorrelated / server-initiated: no originating request, so method + request stay null and
        // the passive runner correctly skips it (no host to invent, no content-kind to resolve).
        assertThat(standalone.method()).isNull();
        assertThat(standalone.request()).isNull();

        // The unrelated pending request for id "1" is untouched: its response still correlates and links.
        McpExchange request = exchanges.get(0);
        log.observe(productionResponse("s", "1", 200, mock(JsonNode.class)));
        List<McpExchange> after = log.exchanges();
        assertThat(after).hasSize(3);
        assertThat(after.get(2).link()).isEqualTo(request.link());
    }

    @Test
    void responseIsLinkedNotMergedLeavingTheRequestRowUntouched() {
        log.observe(clientToServer("s", "42", "tools/call"));
        McpExchange originalRequest = log.exchanges().get(0);

        log.observe(productionResponse("s", "42", 200, mock(JsonNode.class)));

        List<McpExchange> exchanges = log.exchanges();
        assertThat(exchanges).hasSize(2);
        // The original request row object is still present, unchanged: two distinct rows, opposite
        // directions, same link — linked, not merged. The response row borrows the request's method +
        // request for scanning, but the request ROW itself is the same untouched object.
        assertThat(exchanges.get(0)).isSameAs(originalRequest);
        assertThat(exchanges.get(0).direction()).isEqualTo(Direction.CLIENT_TO_SERVER);
        assertThat(exchanges.get(0).status()).isNull();
        assertThat(exchanges.get(0).decodedResponse()).isNull();
        assertThat(exchanges.get(1).direction()).isEqualTo(Direction.SERVER_TO_CLIENT);
        assertThat(exchanges.get(1).link()).isEqualTo(exchanges.get(0).link());
        assertThat(exchanges.get(1).method()).isEqualTo(originalRequest.method());
        assertThat(exchanges.get(1).request()).isSameAs(originalRequest.request());
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
    void serverToClientObservationTriggersPassiveRunnerOnceWithTheCorrelatedResponseRow() {
        ObservedMessage request = clientToServer("s", "42", "tools/call");
        log.observe(request);

        log.observe(productionResponse("s", "42", 200, mock(JsonNode.class)));

        assertThat(passiveRunner.scanned).hasSize(1);
        McpExchange scanned = passiveRunner.scanned.get(0);
        assertThat(scanned.direction()).isEqualTo(Direction.SERVER_TO_CLIENT);
        assertThat(scanned.jsonrpcId()).isEqualTo("42");
        assertThat(scanned.status()).isEqualTo(200);
        // The runner sees the enriched response row carrying the originating request's method + request.
        assertThat(scanned.method()).isEqualTo("tools/call");
        assertThat(scanned.request()).isSameAs(request.request());
        assertThat(scanned).isSameAs(log.exchanges().get(1));
    }

    @Test
    void clientToServerObservationDoesNotTriggerPassiveRunner() {
        log.observe(clientToServer("s", "42", "tools/call"));

        assertThat(passiveRunner.scanned).isEmpty();
    }

    @Test
    void appendListenerIsNotifiedForEveryAppendedRowInOrder() {
        List<McpExchange> appended = new ArrayList<>();
        log.setAppendListener(appended::add);

        log.observe(clientToServer("s", "42", "tools/call"));
        log.observe(productionResponse("s", "42", 200, mock(JsonNode.class)));

        assertThat(appended).hasSize(2);
        assertThat(appended.get(0).direction()).isEqualTo(Direction.CLIENT_TO_SERVER);
        assertThat(appended.get(1).direction()).isEqualTo(Direction.SERVER_TO_CLIENT);
        // The listener receives the same row objects the log holds, in append order.
        assertThat(appended).containsExactlyElementsOf(log.exchanges());
    }

    @Test
    void appendListenerDefaultsToNoOpSoExistingCallersAreUnaffected() {
        log.observe(clientToServer("s", "1", "tools/call"));

        assertThat(log.exchanges()).hasSize(1);
    }

    @Test
    void nullAppendListenerResetsToNoOpAndDoesNotThrow() {
        log.setAppendListener(null);

        log.observe(clientToServer("s", "1", "tools/call"));

        assertThat(log.exchanges()).hasSize(1);
    }

    private static ObservedMessage clientToServer(String sessionId, String jsonrpcId, String method) {
        return new ObservedMessage(Direction.CLIENT_TO_SERVER, TransportType.STREAMABLE_HTTP, sessionId, jsonrpcId,
                method, mock(HttpRequest.class), mock(JsonNode.class), null);
    }

    private static ObservedMessage productionResponse(String sessionId, String jsonrpcId,
                                                       Integer status, JsonNode parsed) {
        // Production shape (see BurpObserverAdapter.observeResponse): a JSON-RPC response envelope has
        // no method and the response row carries no request — both are null. recordResponse must enrich
        // the row from the correlated request.
        return new ObservedMessage(Direction.SERVER_TO_CLIENT, TransportType.STREAMABLE_HTTP, sessionId, jsonrpcId,
                null, null, parsed, status);
    }

    private static final class CapturingPassiveRunner implements PassiveLiveRunner {
        private final List<McpExchange> scanned = new ArrayList<>();

        @Override
        public void scan(McpExchange exchange) {
            scanned.add(exchange);
        }
    }
}
