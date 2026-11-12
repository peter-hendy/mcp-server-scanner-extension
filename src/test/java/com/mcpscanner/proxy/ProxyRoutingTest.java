package com.mcpscanner.proxy;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.client.ConnectedEndpoint;
import com.mcpscanner.client.TransportType;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyRoutingTest {

    private static final String ENDPOINT = "https://mcp.example.com/mcp";

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void answersAScannerRequestToTheEndpointLocally() {
        assertThat(routing(TransportType.STREAMABLE_HTTP).answersLocally(endpointRequest()
                .withAddedHeader(ScannerSentinels.STRIP_AUTH_HEADER, ScannerSentinels.SENTINEL_VALUE))).isTrue();
    }

    @Test
    void sendsAnUnmarkedHttpRequestUpstream() {
        assertThat(routing(TransportType.STREAMABLE_HTTP).answersLocally(endpointRequest())).isFalse();
    }

    @Test
    void answersEveryRequestToAnSseEndpointLocally() {
        assertThat(routing(TransportType.SSE).answersLocally(endpointRequest())).isTrue();
    }

    @Test
    void sendsADirectDeliveryUpstream() {
        assertThat(routing(TransportType.SSE).answersLocally(endpointRequest()
                .withAddedHeader(ScannerSentinels.DIRECT_DELIVERY_HEADER, ScannerSentinels.SENTINEL_VALUE))).isFalse();
    }

    @Test
    void sendsUpstreamWhileNoTransportIsKnown() {
        assertThat(routing(null).answersLocally(endpointRequest())).isFalse();
    }

    private static HttpRequest endpointRequest() {
        return HttpRequest.httpRequestFromUrl(ENDPOINT);
    }

    private static ProxyRouting routing(TransportType transport) {
        return new ProxyRouting(new ConnectedEndpoint() {
            @Override
            public TransportType transportType() {
                return transport;
            }

            @Override
            public String resolvedEndpoint() {
                return ENDPOINT;
            }
        });
    }
}
