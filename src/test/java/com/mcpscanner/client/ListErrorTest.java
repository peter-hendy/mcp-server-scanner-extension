package com.mcpscanner.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ListErrorTest {

    @Test
    void transportFailureNamesTheRootCauseRatherThanTheWrapper() {
        RuntimeException failure = new IllegalStateException("MCP request failed",
                new IOException("wrapped", new IOException("Connection reset")));

        assertThat(ListError.from(failure).describe()).isEqualTo("transport failure: Connection reset");
    }

    @Test
    void transportFailureSkipsCausesWithoutAMessage() {
        RuntimeException failure = new IllegalStateException("MCP request failed",
                new IOException("Connection refused", new ConnectException()));

        assertThat(ListError.from(failure).describe()).isEqualTo("transport failure: Connection refused");
    }

    @Test
    void transportFailureNamesTheRootCauseTypeWhenNothingHasAMessage() {
        RuntimeException failure = new IllegalStateException(null, new ConnectException());

        assertThat(ListError.from(failure).describe()).isEqualTo("transport failure: ConnectException");
    }

    @Test
    void transportFailureSurvivesACyclicCauseChain() {
        IOException first = new IOException("first");
        IllegalStateException failure = new IllegalStateException("top", first);
        first.initCause(failure);

        assertThat(ListError.from(failure).describe()).isEqualTo("transport failure: first");
    }

    @Test
    void httpStatusFailureIsDescribedByItsStatus() {
        assertThat(ListError.from(new McpHttpStatusException("tools/list", 401)).describe()).isEqualTo("HTTP 401");
    }

    @Test
    void jsonRpcErrorKeepsCodeAndMessage() {
        assertThat(ListError.fromJsonRpc(Map.of("code", -32601, "message", "Method not found")).describe())
                .isEqualTo("JSON-RPC error -32601: Method not found");
    }

    @Test
    void jsonRpcErrorWithoutCodeOrMessageStillDescribes() {
        assertThat(ListError.fromJsonRpc(Map.of()).describe()).isEqualTo("JSON-RPC error 0: ");
    }
}
