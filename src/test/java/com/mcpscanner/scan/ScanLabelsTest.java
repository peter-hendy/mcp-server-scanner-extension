package com.mcpscanner.scan;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.engine.ScanLabel;
import com.mcpscanner.mcp.McpRequestNames;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScanLabelsTest {

    private final ScanLabels labels = new ScanLabels(new FixedConnectionFacts("SSE 2024-11-05", "Alice"),
            new McpRequestNames());

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void labelsAOneRequestScanWithItsMcpSubjectHostTransportAndConnectAsUser() {
        HttpRequest call = HttpRequest.httpRequestFromUrl("https://mcp.example:8443/mcp").withMethod("POST")
                .withBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"read_file\"}}");

        assertThat(labels.of(List.of(unsent(call)))).isEqualTo(
                new ScanLabel("Scan tools/call read_file", "mcp.example", "SSE 2024-11-05", "Alice",
                        "https://mcp.example:8443"));
    }

    @Test
    void namesAScanOfSeveralRequestsByCount() {
        HttpRequest request = HttpRequest.httpRequestFromUrl("https://mcp.example/mcp");

        assertThat(labels.of(List.of(unsent(request), unsent(request))).name()).isEqualTo("Scan 2 requests");
    }

    @Test
    void aScanWithNoRequestsHasNoHost() {
        assertThat(labels.of(List.of()).host()).isEmpty();
        assertThat(labels.of(List.of()).origin()).isEmpty();
    }

    @Test
    void bracketsAnIpv6Origin() {
        HttpRequest request = HttpRequest.httpRequestFromUrl("http://[::1]:8080/mcp");

        assertThat(labels.of(List.of(unsent(request))).origin()).isEqualTo("http://[::1]:8080");
    }

    @Test
    void theOriginLeavesOutADefaultPort() {
        HttpRequest request = HttpRequest.httpRequestFromUrl("http://127.0.0.1/mcp");

        assertThat(labels.of(List.of(unsent(request))).origin()).isEqualTo("http://127.0.0.1");
    }

    private static HttpRequestResponse unsent(HttpRequest request) {
        return HttpRequestResponse.httpRequestResponse(request, null);
    }
}
