package com.mcpscanner.proxy.observe;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.client.TransportType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class NoopObservationSinkTest {

    private final McpObservationSink sink = new NoopObservationSink();

    @Test
    void observeAcceptsFullyPopulatedMessageWithoutThrowing() {
        ObservedMessage message = new ObservedMessage(
                Direction.CLIENT_TO_SERVER,
                TransportType.STREAMABLE_HTTP,
                "session-1",
                "rpc-1",
                "tools/call",
                mock(HttpRequest.class),
                mock(JsonNode.class),
                200);

        assertThatCode(() -> sink.observe(message)).doesNotThrowAnyException();
    }

    @Test
    void observeAcceptsNullHeavyMessageWithoutThrowing() {
        ObservedMessage message = new ObservedMessage(
                Direction.SERVER_TO_CLIENT,
                TransportType.SSE,
                null,
                null,
                null,
                null,
                null,
                null);

        assertThatCode(() -> sink.observe(message)).doesNotThrowAnyException();
    }
}
