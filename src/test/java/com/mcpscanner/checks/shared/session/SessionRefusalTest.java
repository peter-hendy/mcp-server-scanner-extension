package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SessionRefusalTest {

    private static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"ping\"}";

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404})
    void aClientErrorThatRejectsTheRequestIsARefusal(int status) {
        assertThat(SessionRefusal.refuses(exchange(status, ""))).isTrue();
    }

    @Test
    void aJsonRpcErrorAnsweringTheRequestIsARefusal() {
        assertThat(SessionRefusal.refuses(exchange(200,
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"error\":{\"code\":-32600,\"message\":\"bad session\"}}"))).isTrue();
    }

    @Test
    void aJsonRpcErrorOnAThrottledResponseIsInconclusive() {
        assertThat(SessionRefusal.refuses(exchange(429,
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"error\":{\"code\":-32000,\"message\":\"slow down\"}}"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {202, 302, 429, 500, 502, 503})
    void throttlingServerErrorsRedirectsAndAcceptanceAreInconclusive(int status) {
        assertThat(SessionRefusal.refuses(exchange(status, ""))).isFalse();
    }

    @Test
    void aSuccessfulResultOrNoResponseIsNotARefusal() {
        assertThat(SessionRefusal.refuses(exchange(200, "{\"jsonrpc\":\"2.0\",\"id\":4,\"result\":{}}"))).isFalse();
        assertThat(SessionRefusal.refuses(HttpRequestResponse.httpRequestResponse(request(), null))).isFalse();
        assertThat(SessionRefusal.refuses(null)).isFalse();
    }

    @Test
    void onlyUnauthorizedAndForbiddenAskForCredentials() {
        assertThat(SessionRefusal.requiresCredentials(exchange(401, ""))).isTrue();
        assertThat(SessionRefusal.requiresCredentials(exchange(403, ""))).isTrue();
        assertThat(SessionRefusal.requiresCredentials(exchange(404, ""))).isFalse();
        assertThat(SessionRefusal.requiresCredentials(exchange(429, ""))).isFalse();
    }

    private static HttpRequestResponse exchange(int status, String body) {
        return HttpRequestResponse.httpRequestResponse(request(),
                HttpResponse.httpResponse("HTTP/1.1 " + status + " X\r\nContent-Type: application/json\r\n\r\n" + body));
    }

    private static HttpRequest request() {
        return HttpRequest.httpRequestFromUrl("http://t.test/mcp").withMethod("POST").withBody(PING);
    }
}
