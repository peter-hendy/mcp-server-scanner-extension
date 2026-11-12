package com.mcpscanner.proxy;

import com.mcpscanner.mcp.McpHeaders;
import com.mcpscanner.mcp.ScannerSentinels;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ReservedHeaderPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Host", "content-length", "Content-Type", "ACCEPT", "Connection", "Upgrade",
            "Transfer-Encoding", "HTTP2-Settings", "Proxy-Connection", "Expect", "Keep-Alive", "TE",
            "Trailer", "Trailers", "Date", "From", "Via", "Warning"})
    void transportFramingHeadersAreNeitherForwardedNorIdentityBearing(String name) {
        assertThat(ReservedHeaderPolicy.isTransportFraming(name)).isTrue();
        assertThat(ReservedHeaderPolicy.isForwardable(name)).isFalse();
        assertThat(ReservedHeaderPolicy.isIdentityBearing(name)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {ScannerSentinels.STRIP_AUTH_HEADER, ScannerSentinels.AUDIT_CAPABILITY_HEADER,
            ScannerSentinels.KEEP_SESSION_HEADER})
    void scannerSentinelsAreNeitherForwardedNorIdentityBearing(String name) {
        assertThat(ReservedHeaderPolicy.isForwardable(name)).isFalse();
        assertThat(ReservedHeaderPolicy.isIdentityBearing(name)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {McpHeaders.SESSION_ID, McpHeaders.PROTOCOL_VERSION, McpHeaders.METHOD, McpHeaders.NAME})
    void mcpProtocolHeadersAreManagedByTheSessionNotIdentityBearing(String name) {
        assertThat(McpHeaders.isProtocolHeader(name)).isTrue();
        assertThat(ReservedHeaderPolicy.isIdentityBearing(name)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"User-Agent", "remote-addr", "http-client-ip"})
    void clientAndListenerSyntheticHeadersAreNotIdentityBearing(String name) {
        assertThat(ReservedHeaderPolicy.isIdentityBearing(name)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Authorization", "Cookie", "X-Api-Key"})
    void credentialHeadersAreForwardableAndIdentityBearing(String name) {
        assertThat(ReservedHeaderPolicy.isForwardable(name)).isTrue();
        assertThat(ReservedHeaderPolicy.isIdentityBearing(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Proxy-Authorization"})
    void proxyCredentialsAreIdentityBearingButNeverForwarded(String name) {
        assertThat(ReservedHeaderPolicy.isTransportFraming(name)).isFalse();
        assertThat(ReservedHeaderPolicy.isForwardable(name)).isFalse();
        assertThat(ReservedHeaderPolicy.isIdentityBearing(name)).isTrue();
    }
}
