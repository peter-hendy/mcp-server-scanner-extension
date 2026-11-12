package com.mcpscanner.proxy;

import com.mcpscanner.client.ProtocolHttpRequest;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.ScriptedProtocolTransport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SentinelStrippingTransportTest {

    @Test
    void handsTheDelegateTheRequestWithoutAnyScannerSentinelInAnyCase() throws Exception {
        ScriptedProtocolTransport delegate = ScriptedProtocolTransport.fakeServer();
        ProtocolHttpRequest request = new ProtocolHttpRequest(URI.create("https://upstream.test/mcp"), "POST",
                Map.of(ScannerSentinels.STRIP_AUTH_HEADER.toLowerCase(Locale.ROOT), "1",
                        ScannerSentinels.AUDIT_CAPABILITY_HEADER.toUpperCase(Locale.ROOT), "capability",
                        ScannerSentinels.KEEP_SESSION_HEADER, "1",
                        "Authorization", "Bearer kept"), "{}");

        new SentinelStrippingTransport(delegate).send(request, Duration.ofSeconds(1));

        assertThat(delegate.requests()).singleElement().satisfies(sent -> {
            assertThat(sent.headers()).containsOnly(Map.entry("Authorization", "Bearer kept"));
            assertThat(sent.uri()).isEqualTo(request.uri());
            assertThat(sent.method()).isEqualTo("POST");
            assertThat(sent.body()).isEqualTo("{}");
        });
    }

    @Test
    void handsTheDelegateTheRequestWithoutTheIdentitySentinel() throws Exception {
        ScriptedProtocolTransport delegate = ScriptedProtocolTransport.fakeServer();
        ProtocolHttpRequest request = new ProtocolHttpRequest(URI.create("https://upstream.test/mcp"), "POST",
                Map.of(ScannerSentinels.IDENTITY_HEADER.toLowerCase(Locale.ROOT), "bob",
                        "Authorization", "Bearer bob"), "{}");

        new SentinelStrippingTransport(delegate).send(request, Duration.ofSeconds(1));

        assertThat(delegate.requests()).singleElement()
                .satisfies(sent -> assertThat(sent.headers()).containsOnly(Map.entry("Authorization", "Bearer bob")));
    }

    @Test
    void preservesTheOrderOfTheRemainingHeaders() throws Exception {
        ScriptedProtocolTransport delegate = ScriptedProtocolTransport.fakeServer();
        Map<String, String> headers = new LinkedHashMap<>();
        for (int index = 0; index < 20; index++) {
            headers.put("X-Header-" + index, String.valueOf(index));
        }
        headers.put(ScannerSentinels.STRIP_AUTH_HEADER, "1");
        headers.put("Content-Type", "application/json");

        new SentinelStrippingTransport(delegate).send(new ProtocolHttpRequest(URI.create("https://upstream.test/mcp"),
                "POST", headers, "{}"), Duration.ofSeconds(1));

        Map<String, String> expected = new LinkedHashMap<>(headers);
        expected.remove(ScannerSentinels.STRIP_AUTH_HEADER);
        assertThat(delegate.requests()).singleElement()
                .satisfies(sent -> assertThat(sent.headers().keySet()).containsExactlyElementsOf(expected.keySet()));
    }
}
