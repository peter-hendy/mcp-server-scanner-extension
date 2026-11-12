package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResponseHeadersTest {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void valuesMatchNamesCaseInsensitivelyAndKeepRepeats() {
        HttpResponse response = HttpResponse.httpResponse("HTTP/1.1 200 OK\r\ncontent-security-policy: a\r\n"
                + "Content-Security-Policy: b\r\n\r\n");

        assertThat(ResponseHeaders.values(response, "Content-Security-Policy")).containsExactly("a", "b");
        assertThat(ResponseHeaders.first(response, "CONTENT-SECURITY-POLICY")).contains("a");
        assertThat(ResponseHeaders.first(response, "Location")).isEmpty();
    }

    @Test
    void htmlIsRecognisedFromTheContentType() {
        assertThat(ResponseHeaders.isHtml(HttpResponse.httpResponse(
                "HTTP/1.1 200 OK\r\ncontent-type: Text/HTML; charset=utf-8\r\n\r\n"))).isTrue();
        assertThat(ResponseHeaders.isHtml(HttpResponse.httpResponse(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n"))).isFalse();
        assertThat(ResponseHeaders.isHtml(HttpResponse.httpResponse("HTTP/1.1 200 OK\r\n\r\n"))).isFalse();
        assertThat(ResponseHeaders.isHtml(HttpResponse.httpResponse(
                "HTTP/1.1 200 OK\r\nContent-Type: application/xhtml+xml\r\n\r\n"))).isFalse();
    }
}
