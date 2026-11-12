package com.mcpscanner.checks.catalog.originhostadmission;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.checks.shared.auth.CredentialHeaders;
import com.mcpscanner.mcp.ScannerSentinels;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProbeDeliveryTest {

    private final CredentialHeaders credentials = new CredentialHeaders(() -> new BearerTokenAuthStrategy("t"));
    private final HttpRequest seed = AdmissionServers.toolsList(AdmissionServers.LOOPBACK_URL);

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    @Test
    void directDeliveryAsksTheExtensionToBypassItsHostRewritingProxy() {
        HttpRequest prepared = ProbeDelivery.direct(credentials).apply(seed);

        assertThat(prepared.hasHeader(ScannerSentinels.DIRECT_DELIVERY_HEADER)).isTrue();
        assertThat(prepared.hasHeader(ScannerSentinels.STRIP_AUTH_HEADER)).isFalse();
    }

    @Test
    void ownStreamDeliveryAsksTheSseProxyForAStreamCarryingOnlyTheRequestsHeaders() {
        HttpRequest prepared = ProbeDelivery.ownStream(credentials).apply(seed);

        assertThat(prepared.hasHeader(ScannerSentinels.STRIP_AUTH_HEADER)).isTrue();
        assertThat(prepared.hasHeader(ScannerSentinels.DIRECT_DELIVERY_HEADER)).isFalse();
    }

    @Test
    void bothSupplyTheCredentialsTheProxyWouldOtherwiseInject() {
        assertThat(ProbeDelivery.direct(credentials).apply(seed).headerValue("Authorization")).isEqualTo("Bearer t");
        assertThat(ProbeDelivery.ownStream(credentials).apply(seed).headerValue("Authorization"))
                .isEqualTo("Bearer t");
    }

    @Test
    void leavesTheBodyAndCapturedHeadersAlone() {
        HttpRequest prepared = ProbeDelivery.direct(credentials).apply(seed);

        assertThat(prepared.bodyToString()).isEqualTo(seed.bodyToString());
        assertThat(prepared.headerValue("Host")).isEqualTo(seed.headerValue("Host"));
    }
}
