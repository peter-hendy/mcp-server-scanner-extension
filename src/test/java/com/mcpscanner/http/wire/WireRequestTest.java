package com.mcpscanner.http.wire;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class WireRequestTest {

    @BeforeEach
    void installMontoya() {
        MontoyaTestFactory.install();
    }

    @Test
    void writesTheProbeHeadersVerbatimInOrderAfterADefaultHostAndAddsConnectionClose() {
        HttpRequest request = HttpRequest.httpRequestFromUrl("http://127.0.0.1:8000/mcp?x=1").withMethod("POST")
                .withHeader("content-type", "application/json")
                .withHeader("X-Probe", "a b")
                .withBody("{}");

        assertThat(text(WireRequest.of(request))).isEqualTo("POST /mcp?x=1 HTTP/1.1\r\n"
                + "Host: 127.0.0.1:8000\r\n"
                + "content-type: application/json\r\n"
                + "X-Probe: a b\r\n"
                + "Connection: close\r\n"
                + "Content-Length: 2\r\n"
                + "\r\n{}");
    }

    @Test
    void keepsAProbeHostAndConnectionAndRecomputesFraming() {
        HttpRequest request = HttpRequest.httpRequestFromUrl("http://127.0.0.1:8000/mcp").withMethod("POST")
                .withHeader("Host", "attacker.example")
                .withHeader("Connection", "keep-alive")
                .withHeader("Content-Length", "999")
                .withHeader("Transfer-Encoding", "chunked")
                .withBody("é");

        assertThat(text(WireRequest.of(request))).isEqualTo("POST /mcp HTTP/1.1\r\n"
                + "Host: attacker.example\r\n"
                + "Connection: keep-alive\r\n"
                + "Content-Length: 2\r\n"
                + "\r\né");
    }

    @Test
    void sendsTheRequestsRawBodyBytesVerbatim() {
        byte[] latin1Body = {'{', (byte) 0xE9, '}'};
        HttpRequest base = HttpRequest.httpRequestFromUrl("http://127.0.0.1:8000/mcp").withMethod("POST");
        HttpRequest request = mock(HttpRequest.class, delegatesTo(base));
        doReturn(ByteArray.byteArray(latin1Body)).when(request).body();
        doReturn(new String(latin1Body, StandardCharsets.ISO_8859_1)).when(request).bodyToString();

        WireRequest wire = WireRequest.of(request);
        byte[] bytes = wire.bytes();

        assertThat(text(wire)).contains("Content-Length: 3\r\n");
        assertThat(Arrays.copyOfRange(bytes, bytes.length - latin1Body.length, bytes.length)).isEqualTo(latin1Body);
    }

    @Test
    void sendsABodylessGetWithoutContentLength() {
        HttpRequest request = HttpRequest.httpRequestFromUrl("http://127.0.0.1:8000/sse");

        assertThat(text(WireRequest.of(request))).isEqualTo("GET /sse HTTP/1.1\r\n"
                + "Host: 127.0.0.1:8000\r\nConnection: close\r\n\r\n");
    }

    @Test
    void connectsToTheRequestServiceRatherThanItsUrlAsBurpDoes() {
        HttpRequest request = HttpRequest.httpRequestFromUrl("http://upstream.example:9000/messages")
                .withService(HttpService.httpService("127.0.0.1", 4321, false));

        WireRequest wire = WireRequest.of(request);

        assertThat(wire.host()).isEqualTo("127.0.0.1");
        assertThat(wire.port()).isEqualTo(4321);
        assertThat(wire.secure()).isFalse();
        assertThat(text(wire)).contains("Host: upstream.example:9000\r\n");
    }

    private static String text(WireRequest wire) {
        return new String(wire.bytes(), StandardCharsets.UTF_8);
    }
}
