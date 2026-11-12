package com.mcpscanner.checks.catalog.commandinjection;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldSubjectTest {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void namesTheToolOfAToolCall() {
        FieldSubject subject = FieldSubject.of(request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"ping_host\",\"arguments\":{\"host\":\"x\"}}}"), "MCP field: params.arguments.host");

        assertThat(subject.operation()).isEqualTo("ping_host");
        assertThat(subject.method()).isEqualTo("tools/call");
        assertThat(subject.url()).isEqualTo("http://mcp.example/mcp");
    }

    @Test
    void namesTheMethodOfAnyOtherRequest() {
        FieldSubject subject = FieldSubject.of(request("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\","
                + "\"params\":{\"uri\":\"file:///x\"}}"), "MCP field: params.uri");

        assertThat(subject.operation()).isEqualTo("resources/read");
        assertThat(subject.tool()).isEmpty();
    }

    @Test
    void toleratesAnUnparseableBodyAndMissingName() {
        FieldSubject subject = FieldSubject.of(request("not json"), null);

        assertThat(subject.operation()).isEmpty();
        assertThat(subject.field()).isEmpty();
    }

    private static HttpRequest request(String body) {
        return HttpRequest.httpRequestFromUrl("http://mcp.example/mcp").withMethod("POST").withBody(body);
    }
}
