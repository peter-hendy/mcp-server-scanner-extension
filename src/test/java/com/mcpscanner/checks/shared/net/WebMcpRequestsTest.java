package com.mcpscanner.checks.shared.net;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.ImmutableHttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebMcpRequestsTest {

    private static final String TOOLS_LIST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}";

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void acceptsAnMcpRequestToAWebUrl() {
        assertThat(WebMcpRequests.isWebMcpRequest(exchange("https://mcp.example/mcp", TOOLS_LIST))).isTrue();
    }

    @Test
    void rejectsARequestThatIsNotMcp() {
        assertThat(WebMcpRequests.isWebMcpRequest(exchange("https://mcp.example/index.html", "hello"))).isFalse();
    }

    @Test
    void rejectsAMissingRequest() {
        assertThat(WebMcpRequests.isWebMcpRequest(mock(HttpRequestResponse.class))).isFalse();
    }

    private static HttpRequestResponse exchange(String url, String body) {
        return HttpRequestResponse.httpRequestResponse(
                ImmutableHttpRequest.post(url, Map.of("Content-Type", "application/json"), body),
                HttpResponse.httpResponse("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n{}"));
    }
}
