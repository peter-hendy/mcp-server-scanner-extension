package com.mcpscanner.proxy;

import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyFabricatedReplyTest {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void recognisesTheProxysOwnTimeoutAndDisconnectReplies() {
        assertThat(ProxyFabricatedReply.matches(reply(502, "{\"error\":\"Timed out waiting for SSE response\"}"))).isTrue();
        assertThat(ProxyFabricatedReply.matches(reply(503, "{\"error\":\"MCP server not connected\"}"))).isTrue();
    }

    @Test
    void treatsUpstreamRepliesAsReachingTheTarget() {
        assertThat(ProxyFabricatedReply.matches(reply(401, "{\"error\":\"SSE authentication required\"}"))).isFalse();
        assertThat(ProxyFabricatedReply.matches(reply(502, "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32603}}"))).isFalse();
        assertThat(ProxyFabricatedReply.matches(reply(502, "<html>bad gateway</html>"))).isFalse();
        assertThat(ProxyFabricatedReply.matches(reply(200, "{\"error\":\"x\"}"))).isFalse();
    }

    private static HttpResponse reply(int status, String body) {
        return HttpResponse.httpResponse("HTTP/1.1 " + status + " \r\nContent-Type: application/json\r\n\r\n" + body);
    }
}
