package com.mcpscanner.checks.shared.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRpcEnvelopeTest {

    private static final String TOOL_CALL =
            "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"read\",\"uri\":\"file:///x\"}}";

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void readsTheMethodIdAndRoutingParamsOfARequest() {
        JsonRpcEnvelope envelope = JsonRpcEnvelope.of(ImmutableHttpRequest.post("http://mcp.example/mcp", Map.of(),
                TOOL_CALL));

        assertThat(envelope.singleMessage()).isTrue();
        assertThat(envelope.isJsonRpc2()).isTrue();
        assertThat(envelope.declaresJsonRpcVersion()).isTrue();
        assertThat(envelope.method()).contains("tools/call");
        assertThat(envelope.id()).hasValueSatisfying(id -> assertThat(id.asInt()).isEqualTo(7));
        assertThat(envelope.hasScalarId()).isTrue();
        assertThat(envelope.notification()).isFalse();
        assertThat(envelope.paramsName()).contains("read");
        assertThat(envelope.paramsUri()).contains("file:///x");
    }

    @Test
    void parsesBytesTheSameWayAsText() {
        JsonRpcEnvelope envelope = JsonRpcEnvelope.of(TOOL_CALL.getBytes(StandardCharsets.UTF_8));

        assertThat(envelope.method()).contains("tools/call");
        assertThat(envelope.paramsName()).contains("read");
    }

    @Test
    void readsOnlyTextualMethodsAndRoutingParams() {
        JsonRpcEnvelope envelope = JsonRpcEnvelope.of("{\"jsonrpc\":1,\"method\":5,\"params\":{\"name\":{},\"uri\":3}}");

        assertThat(envelope.method()).isEmpty();
        assertThat(envelope.paramsName()).isEmpty();
        assertThat(envelope.paramsUri()).isEmpty();
        assertThat(envelope.declaresJsonRpcVersion()).isFalse();
        assertThat(envelope.isJsonRpc2()).isFalse();
    }

    @Test
    void treatsAMissingOrNullIdAsANotification() {
        assertThat(JsonRpcEnvelope.of("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}").notification())
                .isTrue();
        assertThat(JsonRpcEnvelope.of("{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}").notification()).isTrue();
        assertThat(JsonRpcEnvelope.of("{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}").id()).isEmpty();
    }

    @Test
    void acceptsOnlyTextOrNumberAsAScalarId() {
        assertThat(JsonRpcEnvelope.of("{\"id\":\"a\"}").hasScalarId()).isTrue();
        assertThat(JsonRpcEnvelope.of("{\"id\":1.5}").hasScalarId()).isTrue();
        assertThat(JsonRpcEnvelope.of("{\"id\":true}").hasScalarId()).isFalse();
        assertThat(JsonRpcEnvelope.of("{\"id\":{}}").hasScalarId()).isFalse();
        assertThat(JsonRpcEnvelope.of("{}").hasScalarId()).isFalse();
    }

    @Test
    void aBatchIsNeitherASingleMessageNorANotification() {
        JsonRpcEnvelope batch = JsonRpcEnvelope.of("[{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}]");

        assertThat(batch.root()).isPresent();
        assertThat(batch.singleMessage()).isFalse();
        assertThat(batch.notification()).isFalse();
        assertThat(batch.method()).isEmpty();
        assertThat(batch.id()).isEmpty();
    }

    @Test
    void aMalformedOrMissingBodyHasNoEnvelope() {
        JsonRpcEnvelope malformed = JsonRpcEnvelope.of("{\"jsonrpc\":");

        assertThat(malformed.root()).isEmpty();
        assertThat(malformed.singleMessage()).isFalse();
        assertThat(malformed.notification()).isFalse();
        assertThat(JsonRpcEnvelope.of((burp.api.montoya.http.message.requests.HttpRequest) null).root()).isEmpty();
        assertThat(JsonRpcEnvelope.of("").singleMessage()).isFalse();
    }

    @Test
    void matchesNumericIdsByValueAndTextualIdsExactly() {
        JsonRpcEnvelope numeric = JsonRpcEnvelope.of("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}");
        JsonRpcEnvelope textual = JsonRpcEnvelope.of("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"ping\"}");

        assertThat(numeric.isAnsweredBy(message("{\"id\":1.0}"))).isTrue();
        assertThat(numeric.isAnsweredBy(message("{\"id\":\"1\"}"))).isFalse();
        assertThat(textual.isAnsweredBy(message("{\"id\":\"1\"}"))).isTrue();
        assertThat(textual.isAnsweredBy(message("{\"id\":1}"))).isFalse();
        assertThat(numeric.isAnsweredBy(message("{\"result\":{}}"))).isFalse();
    }

    @Test
    void aNotificationIsAnsweredByNothing() {
        JsonRpcEnvelope notification = JsonRpcEnvelope.of("{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}");

        assertThat(notification.isAnsweredBy(message("{\"id\":null}"))).isFalse();
        assertThat(notification.isAnsweredBy(message("{}"))).isFalse();
    }

    private static JsonNode message(String json) {
        return JsonRpcMessages.parsed(json).orElseThrow();
    }
}
