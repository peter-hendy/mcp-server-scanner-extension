package com.mcpscanner.checks.shared.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRpcMessagesTest {

    @Test
    void readsASingleJsonMessage() {
        assertThat(ids(JsonRpcMessages.in("  {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}"))).containsExactly("1");
    }

    @Test
    void flattensABatchArray() {
        assertThat(ids(JsonRpcMessages.in("[{\"id\":1},{\"id\":\"two\"}]"))).containsExactly("1", "two");
    }

    @Test
    void readsEveryEventOfAnSseBodyAndFlattensBatchesInsideThem() {
        String body = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n"
                + "event: message\ndata: [{\"id\":1},{\"id\":2}]\n\ndata: not json\n\ndata: {\"id\":3}";

        assertThat(JsonRpcMessages.in(body)).hasSize(4);
        assertThat(ids(JsonRpcMessages.in(body))).containsExactly("", "1", "2", "3");
    }

    @Test
    void readsNothingFromAMalformedOrEmptyBody() {
        assertThat(JsonRpcMessages.in("{\"id\":")).isEmpty();
        assertThat(JsonRpcMessages.in("")).isEmpty();
        assertThat(JsonRpcMessages.in(null)).isEmpty();
    }

    @Test
    void parsesTextAndBytesAndRejectsMalformedJson() {
        assertThat(JsonRpcMessages.parsed("{\"a\":1}")).hasValueSatisfying(node -> assertThat(node.has("a")).isTrue());
        assertThat(JsonRpcMessages.parsed("{\"a\":1}".getBytes(StandardCharsets.UTF_8)))
                .hasValueSatisfying(node -> assertThat(node.has("a")).isTrue());
        assertThat(JsonRpcMessages.parsed("{\"a\":")).isEmpty();
        assertThat(JsonRpcMessages.parsed("{\"a\":".getBytes(StandardCharsets.UTF_8))).isEmpty();
        assertThat(JsonRpcMessages.parsed((byte[]) null)).isEmpty();
        assertThat(JsonRpcMessages.parsed((String) null)).isEmpty();
    }

    @Test
    void recognisesAJsonRpc2MessageByItsVersionText() {
        assertThat(JsonRpcMessages.isJsonRpc2(node("{\"jsonrpc\":\"2.0\"}"))).isTrue();
        assertThat(JsonRpcMessages.isJsonRpc2(node("{\"jsonrpc\":2.0}"))).isTrue();
        assertThat(JsonRpcMessages.isJsonRpc2(node("{\"jsonrpc\":\"1.0\"}"))).isFalse();
        assertThat(JsonRpcMessages.isJsonRpc2(node("{}"))).isFalse();
    }

    private static JsonNode node(String json) {
        return JsonRpcMessages.parsed(json).orElseThrow();
    }

    private static List<String> ids(List<JsonNode> messages) {
        return messages.stream().map(message -> message.path("id").asText()).toList();
    }
}
