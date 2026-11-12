package com.mcpscanner.testutil;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MontoyaRequestImmutabilityTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void derivingAProbeLeavesTheBaseRequestUnchanged() {
        HttpRequest base = HttpRequest.httpRequestFromUrl("http://127.0.0.1:1/mcp")
                .withMethod("POST").withHeader("Mcp-Session-Id", "abc").withBody("{\"a\":1}");

        HttpRequest probe = base.withBody("{\"a\":2}").withHeader("Origin", "http://evil.example").withMethod("PUT");

        assertThat(base.bodyToString()).isEqualTo("{\"a\":1}");
        assertThat(base.headerValue("Origin")).isNull();
        assertThat(base.method()).isEqualTo("POST");
        assertThat(probe.bodyToString()).isEqualTo("{\"a\":2}");
        assertThat(probe.headerValue("Origin")).isEqualTo("http://evil.example");
        assertThat(probe.headerValue("Mcp-Session-Id")).isEqualTo("abc");
        assertThat(probe.method()).isEqualTo("PUT");
    }
}
