package com.mcpscanner.checks.shared.jsonrpc;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpReplyTest {

    private static final String CALL = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{}}";

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void readsTheSingleJsonReplyBody() {
        McpReply reply = McpReply.in(response("application/json",
                "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"content\":[]}}"));

        assertThat(reply.answered()).isTrue();
        assertThat(reply.successfulResult()).isPresent();
        assertThat(reply.result().has("content")).isTrue();
    }

    @Test
    void liftsTheIdentifiedMessageOutOfAnSseReplyBody() {
        McpReply reply = McpReply.in(response("text/event-stream",
                "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n"
                        + "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"ok\":true}}\n\n"));

        assertThat(reply.result().path("ok").asBoolean()).isTrue();
    }

    @Test
    void aBatchMalformedOrMissingReplyBodyHasNoMessage() {
        assertThat(McpReply.in(response("application/json", "[{\"id\":5,\"result\":{}}]")).message()).isEmpty();
        assertThat(McpReply.in(response("application/json", "not json")).message()).isEmpty();
        assertThat(McpReply.in(null).answered()).isFalse();
    }

    @Test
    void anObjectWithoutResultOrErrorIsAMessageButNotAnAnswer() {
        McpReply reply = McpReply.in(response("application/json", "{\"jsonrpc\":\"2.0\",\"id\":5}"));

        assertThat(reply.message()).isPresent();
        assertThat(reply.answered()).isFalse();
        assertThat(reply.result().isMissingNode()).isTrue();
    }

    @Test
    void readsTheErrorCodeAndToolErrorFlag() {
        McpReply error = McpReply.in(response("application/json",
                "{\"jsonrpc\":\"2.0\",\"id\":5,\"error\":{\"code\":-32601,\"message\":\"no\"}}"));
        McpReply toolError = McpReply.in(response("application/json",
                "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"isError\":true}}"));

        assertThat(error.answered()).isTrue();
        assertThat(error.errorCode()).isEqualTo(-32601);
        assertThat(error.error().path("message").asText()).isEqualTo("no");
        assertThat(error.successfulResult()).isEmpty();
        assertThat(toolError.toolError()).isTrue();
        assertThat(toolError.successfulResult()).isEmpty();
    }

    @Test
    void aResultBesideAnErrorOrANonObjectResultIsNotSuccessful() {
        assertThat(McpReply.in(response("application/json",
                "{\"id\":5,\"result\":{},\"error\":{\"code\":1}}")).successfulResult()).isEmpty();
        assertThat(McpReply.in(response("application/json", "{\"id\":5,\"result\":\"x\"}")).successfulResult())
                .isEmpty();
    }

    @Test
    void findsTheEligibleMessageAnsweringTheRequestIdInAnyBodyShape() {
        String body = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"sampling/createMessage\"}\n\n"
                + "event: message\ndata: [{\"jsonrpc\":\"2.0\",\"id\":6,\"result\":{}},"
                + "{\"jsonrpc\":\"2.0\",\"id\":5.0,\"result\":{\"n\":1}}]\n\n";

        McpReply reply = McpReply.answering(exchange(CALL, body), McpReply::isResponse);

        assertThat(reply.result().path("n").asInt()).isEqualTo(1);
    }

    @Test
    void theFirstMessageWithTheRequestIdWinsWhenEveryMessageIsEligible() {
        String body = "[{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"sampling/createMessage\"},"
                + "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{}}]";

        McpReply reply = McpReply.answering(exchange(CALL, body), message -> true);

        assertThat(reply.message()).isPresent();
        assertThat(reply.answered()).isFalse();
    }

    @Test
    void answersNothingForAnotherIdATextualIdANotificationOrNoResponse() {
        assertThat(McpReply.answering(exchange(CALL, "{\"id\":6,\"result\":{}}"), McpReply::isResponse).message())
                .isEmpty();
        assertThat(McpReply.answering(exchange(CALL, "{\"id\":\"5\",\"result\":{}}"), McpReply::isResponse).message())
                .isEmpty();
        assertThat(McpReply.answering(exchange("{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}", "{\"result\":{}}"),
                McpReply::isResponse).message()).isEmpty();
        assertThat(McpReply.answering(HttpRequestResponse.httpRequestResponse(request(CALL), null),
                McpReply::isResponse).message()).isEmpty();
        assertThat(McpReply.answering(null, McpReply::isResponse).message()).isEmpty();
    }

    @Test
    void noneHasNoMessage() {
        assertThat(McpReply.none().message()).isEmpty();
        assertThat(McpReply.none().answered()).isFalse();
        assertThat(McpReply.none().result().isMissingNode()).isTrue();
    }

    @Test
    void isResponseNeedsAResultOrAnError() {
        assertThat(McpReply.isResponse(node("{\"result\":null}"))).isTrue();
        assertThat(McpReply.isResponse(node("{\"error\":{}}"))).isTrue();
        assertThat(McpReply.isResponse(node("{\"method\":\"x\"}"))).isFalse();
    }

    private static JsonNode node(String json) {
        return JsonRpcMessages.parsed(json).orElseThrow();
    }

    private static HttpResponse response(String contentType, String body) {
        return HttpResponse.httpResponse("HTTP/1.1 200 OK\r\nContent-Type: " + contentType + "\r\n\r\n" + body);
    }

    private static HttpRequestResponse exchange(String requestBody, String responseBody) {
        return HttpRequestResponse.httpRequestResponse(request(requestBody), response("application/json", responseBody));
    }

    private static burp.api.montoya.http.message.requests.HttpRequest request(String body) {
        return ImmutableHttpRequest.post("http://mcp.example/mcp", Map.of("Content-Type", "application/json"), body);
    }
}
