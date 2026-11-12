package com.mcpscanner.checks.catalog.originhostadmission;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReplaySafeRequestsTest {

    private final ReplaySafeRequests replaySafe = new ReplaySafeRequests();

    @ParameterizedTest
    @ValueSource(strings = {"tools/list", "resources/list", "resources/templates/list", "prompts/list",
            "resources/read", "prompts/get"})
    void acceptsReadOnlyOperationsWithAnId(String method) {
        assertThat(replaySafe.accepts(AdmissionServers.seed(AdmissionServers.LOOPBACK_URL,
                "{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"method\":\"" + method + "\",\"params\":{}}"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"delete_note\"}}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"logging/setLevel\",\"params\":{\"level\":\"debug\"}}",
            "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"tools/list\"}",
            "{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"tools/list\"}",
            "[{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}]",
            "not json",
            ""})
    void rejectsEverythingElse(String body) {
        assertThat(replaySafe.accepts(AdmissionServers.seed(AdmissionServers.LOOPBACK_URL, body))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT"})
    void rejectsNonPostRequests(String method) {
        assertThat(replaySafe.accepts(AdmissionServers.toolsList(AdmissionServers.LOOPBACK_URL).withMethod(method)))
                .isFalse();
    }
}
