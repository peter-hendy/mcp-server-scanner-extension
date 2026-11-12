package com.mcpscanner.checks.api;

import burp.api.montoya.http.HttpService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HostKeyTest {

    @Test
    void originIsTheLowerCasedBaseUrlWithoutTheDefaultPort() {
        assertThat(HostKey.of(service("MCP.Example.com", 8443, true)).origin().baseUrl())
                .isEqualTo("https://mcp.example.com:8443");
        assertThat(HostKey.of(service("127.0.0.1", 80, false)).origin().baseUrl())
                .isEqualTo("http://127.0.0.1");
    }

    @Test
    void sameOriginProducesEqualKeys() {
        assertThat(HostKey.of(service("a.test", 443, true))).isEqualTo(HostKey.of(service("A.TEST", 443, true)));
    }

    @Test
    void schemeAndPortDistinguishHosts() {
        HostKey https = HostKey.of(service("a.test", 443, true));

        assertThat(HostKey.of(service("a.test", 443, false))).isNotEqualTo(https);
        assertThat(HostKey.of(service("a.test", 8443, true))).isNotEqualTo(https);
    }

    static HttpService service(String host, int port, boolean secure) {
        HttpService service = mock(HttpService.class);
        when(service.host()).thenReturn(host);
        when(service.port()).thenReturn(port);
        when(service.secure()).thenReturn(secure);
        return service;
    }
}
