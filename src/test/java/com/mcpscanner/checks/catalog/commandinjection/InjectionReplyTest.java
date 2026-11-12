package com.mcpscanner.checks.catalog.commandinjection;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InjectionReplyTest {

    private static final String CALL = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{}}";

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void aToolResultWithoutErrorIsSuccessfulAndSearchable() {
        InjectionReply reply = reply(200, "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"content\":[{\"type\":\"text\","
                + "\"text\":\"value mcp42\"}]}}");

        assertThat(reply.answered()).isTrue();
        assertThat(reply.successful()).isTrue();
        assertThat(reply.rejected()).isFalse();
        assertThat(reply.contains("mcp42")).isTrue();
        assertThat(reply.contains("mcp43")).isFalse();
    }

    @Test
    void searchesNestedStructuredContentAndResourceContents() {
        InjectionReply reply = reply(200, "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"structuredContent\":{\"rows\":"
                + "[{\"out\":\"x mcp42\"}]},\"contents\":[{\"uri\":\"a\",\"text\":\"y\"}]}}");

        assertThat(reply.contains("mcp42")).isTrue();
    }

    @Test
    void readsTheMatchingMessageFromAnEventStream() {
        InjectionReply reply = reply(200, "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/message\","
                + "\"params\":{\"data\":\"mcp42\"}}\n\nevent: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":5,"
                + "\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"done\"}]}}\n\n");

        assertThat(reply.successful()).isTrue();
        assertThat(reply.contains("mcp42")).isFalse();
    }

    @Test
    void ignoresAReplyForADifferentRequestId() {
        InjectionReply reply = reply(200, "{\"jsonrpc\":\"2.0\",\"id\":6,\"result\":{\"content\":[]}}");

        assertThat(reply.answered()).isFalse();
        assertThat(reply.successful()).isFalse();
    }

    @Test
    void aToolErrorIsRejected() {
        InjectionReply reply = reply(200, "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"isError\":true,\"content\":"
                + "[{\"type\":\"text\",\"text\":\"Error: bad\"}]}}");

        assertThat(reply.successful()).isFalse();
        assertThat(reply.rejected()).isTrue();
    }

    @Test
    void aJsonRpcErrorIsRejectedAndSearchable() {
        InjectionReply reply = reply(200, "{\"jsonrpc\":\"2.0\",\"id\":5,\"error\":{\"code\":-32602,\"message\":\"mcp42\"}}");

        assertThat(reply.rejected()).isTrue();
        assertThat(reply.contains("mcp42")).isTrue();
    }

    @Test
    void anErrorFlagOrAToolInvocationFailureIsNotSuccess() {
        assertThat(reply(200, "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"error\":true}}").successful()).isFalse();
        assertThat(reply(200, "{\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"content\":[{\"type\":\"text\","
                + "\"text\":\"Error invoking tool x\"}]}}").successful()).isFalse();
    }

    @Test
    void anHttpErrorOrMissingResponseIsUnanswered() {
        InjectionReply denied = reply(403, "{\"jsonrpc\":\"2.0\",\"id\":5,\"error\":{\"code\":-1,\"message\":\"no\"}}");
        InjectionReply missing = InjectionReply.of(HttpRequestResponse.httpRequestResponse(request(), null));

        assertThat(denied.answered()).isFalse();
        assertThat(denied.rejected()).isFalse();
        assertThat(missing.answered()).isFalse();
        assertThat(missing.contains("x")).isFalse();
        assertThat(InjectionReply.of(null).successful()).isFalse();
    }

    private static InjectionReply reply(int status, String body) {
        return InjectionReply.of(HttpRequestResponse.httpRequestResponse(request(),
                HttpResponse.httpResponse("HTTP/1.1 " + status + " X\r\nContent-Type: application/json\r\n\r\n" + body)));
    }

    private static HttpRequest request() {
        return HttpRequest.httpRequestFromUrl("http://mcp.example/mcp").withMethod("POST").withBody(CALL);
    }
}
