package com.mcpscanner.http.wire;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SNIHostName;

import static org.assertj.core.api.Assertions.assertThat;

class SniServerNamesTest {

    @Test
    void namesAnOrdinaryHost() {
        assertThat(SniServerNames.forHost("mcp.example")).containsExactly(new SNIHostName("mcp.example"));
    }

    @Test
    void namesNoHostForIpLiterals() {
        assertThat(SniServerNames.forHost("127.0.0.1")).isEmpty();
        assertThat(SniServerNames.forHost("::1")).isEmpty();
    }

    @Test
    void namesNoHostWhenTheHostIsNoValidSniNameLikeADockerServiceName() {
        assertThat(SniServerNames.forHost("mcp_server")).isEmpty();
    }
}
