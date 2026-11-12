package com.mcpscanner.mcp;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpRequestNamesTest {

    private final McpRequestNames names = new McpRequestNames();

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void namesAToolCallByMethodAndTool() {
        assertThat(names.nameOf(post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"read_file\"}}"))).isEqualTo("tools/call read_file");
    }

    @Test
    void namesAResourceReadByMethodAndUri() {
        assertThat(names.nameOf(post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"resources/read\","
                + "\"params\":{\"uri\":\"file:///etc/hosts\"}}"))).isEqualTo("resources/read file:///etc/hosts");
    }

    @Test
    void namesAMessageWithoutASubjectByMethodAlone() {
        assertThat(names.nameOf(post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))).isEqualTo("ping");
    }

    @Test
    void namesANonJsonRpcRequestByMethodAndPath() {
        assertThat(names.nameOf(HttpRequest.httpRequestFromUrl("https://mcp.example/.well-known/oauth")))
                .isEqualTo("GET /.well-known/oauth");
    }

    private static HttpRequest post(String body) {
        return HttpRequest.httpRequestFromUrl("https://mcp.example/mcp").withMethod("POST").withBody(body);
    }
}
