package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcpscanner.checks.shared.probe.SimulatedMcpServer;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedErrorResultTest {

    private final EmbeddedErrorResult embeddedError = new EmbeddedErrorResult();

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"error\":\"No interest list or FBID list provided\"}",
            "{\"data\":\"{\\\"error\\\":\\\"No object ID provided\\\"}\"}",
            "{\"errors\":[{\"message\":\"Not found\"}]}",
            "{\"result\":{\"error\":{\"code\":400}}}"})
    void errorObjectsReturnedAsResultTextAreEmbeddedErrors(String text) {
        assertThat(embeddedError.carriedBy(toolResult(text))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "LOCAL_FIXTURE_SECRET_59822",
            "{\"items\":[{\"organization_name\":\"tenant-a-private-org\"}]}",
            "{\"data\":[{\"id\":\"act_1\",\"error\":null}]}",
            "{\"error\":null,\"value\":42}",
            "{\"logs\":[{\"error\":\"timeout\"}]}",
            "The error handling guide is here"})
    void dataResultsAreNotEmbeddedErrors(String text) {
        assertThat(embeddedError.carriedBy(toolResult(text))).isFalse();
    }

    private static HttpRequestResponse toolResult(String text) {
        ObjectNode reply = McpObjectMapper.INSTANCE.createObjectNode().put("jsonrpc", "2.0").put("id", 11);
        ObjectNode result = reply.putObject("result");
        result.putArray("content").addObject().put("type", "text").put("text", text);
        result.put("isError", false);
        return HttpRequestResponse.httpRequestResponse(
                SimulatedMcpServer.request(SimulatedMcpServer.toolCall(), Map.of()),
                burp.api.montoya.http.message.responses.HttpResponse.httpResponse(
                        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n" + reply));
    }
}
