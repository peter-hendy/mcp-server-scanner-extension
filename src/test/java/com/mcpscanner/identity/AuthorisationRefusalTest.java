package com.mcpscanner.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorisationRefusalTest {

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void anAuthorisationStatusIsARefusal(int status) {
        assertThat(AuthorisationRefusal.refuses(status, "")).isTrue();
    }

    @Test
    void aToolErrorSayingUnauthorisedIsARefusal() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"isError\":true,"
                + "\"content\":[{\"type\":\"text\",\"text\":\"Unauthorized: sign in first\"}]}}";

        assertThat(AuthorisationRefusal.refuses(200, body)).isTrue();
    }

    @Test
    void aToolErrorWithAnUnauthorisedCodeIsARefusal() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"isError\":true,"
                + "\"content\":[{\"type\":\"text\",\"text\":\"{\\\"code\\\":\\\"UNAUTHENTICATED\\\"}\"}]}}";

        assertThat(AuthorisationRefusal.refuses(200, body)).isTrue();
    }

    @Test
    void aJsonRpcErrorSayingAuthenticationIsRequiredIsARefusal() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32001,"
                + "\"message\":\"Authentication required\"}}";

        assertThat(AuthorisationRefusal.refuses(200, body)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Access denied: path outside workspace", "Forbidden characters in argument"})
    void aSandboxOrValidationToolErrorIsNotARefusal(String text) {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"isError\":true,"
                + "\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}]}}";

        assertThat(AuthorisationRefusal.refuses(200, body)).isFalse();
    }

    @Test
    void aJsonRpcErrorSayingForbiddenIsNotARefusal() {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32602,"
                + "\"message\":\"Forbidden characters in argument\"}}";

        assertThat(AuthorisationRefusal.refuses(200, body)).isFalse();
    }

    @Test
    void aSuccessfulOrUnrelatedResultIsNotARefusal() {
        String ok = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"Unauthorized\"}]}}";
        String otherError = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"isError\":true,"
                + "\"content\":[{\"type\":\"text\",\"text\":\"campaign not found\"}]}}";

        assertThat(AuthorisationRefusal.refuses(200, ok)).isFalse();
        assertThat(AuthorisationRefusal.refuses(200, otherError)).isFalse();
        assertThat(AuthorisationRefusal.refuses(200, "not json")).isFalse();
        assertThat(AuthorisationRefusal.refuses(500, "")).isFalse();
    }
}
