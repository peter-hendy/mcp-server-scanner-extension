package com.mcpscanner.checks.shared.session;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.testutil.MontoyaTestFactory;
import com.mcpscanner.core.protocol.McpRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HandshakeVersionTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void keepsTheTemplatesSupportedInitializeVersion() {
        assertThat(HandshakeVersion.of(template().withHeader(McpHeaders.PROTOCOL_VERSION, "2025-03-26")))
                .isEqualTo("2025-03-26");
    }

    @Test
    void fallsBackWhenTheTemplateVersionCannotBeInitialized() {
        assertThat(HandshakeVersion.of(template().withHeader(McpHeaders.PROTOCOL_VERSION, McpRequests.MODERN_VERSION)))
                .isEqualTo(McpRequests.supportedInitializeVersions().get(0));
    }

    @Test
    void fallsBackWhenTheTemplateDeclaresNoVersion() {
        assertThat(HandshakeVersion.of(template())).isEqualTo(McpRequests.supportedInitializeVersions().get(0));
    }

    @Test
    void usesTheCallersVersionWhenTheTemplateDeclaresNone() {
        assertThat(HandshakeVersion.of(template(), "2025-03-26")).isEqualTo("2025-03-26");
    }

    @Test
    void ignoresTheCallersVersionWhenTheTemplateDeclaresAnUnsupportedOne() {
        assertThat(HandshakeVersion.of(template().withHeader(McpHeaders.PROTOCOL_VERSION, McpRequests.MODERN_VERSION),
                "2025-03-26")).isEqualTo(McpRequests.supportedInitializeVersions().get(0));
    }

    private static HttpRequest template() {
        return HttpRequest.httpRequestFromUrl("http://target.test/mcp").withMethod("POST");
    }
}
