package com.mcpscanner.auth.oauth.discovery;

import burp.api.montoya.http.RedirectionMode;
import burp.api.montoya.http.RequestOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.mcpscanner.testutil.RecordingRequestOptions.recorded;
import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryRequestOptionsTest {

    @BeforeEach
    void setUp() {
        com.mcpscanner.testutil.MontoyaTestFactory.install();
    }

    @Test
    void noRedirectPinsNeverMode() {
        // Flow 1 discovery fetches are the SSRF guard: they must never follow a redirect out from
        // under the SuspiciousDestinationGate. RecordingRealHttp ignores the mode, so assert it here on the
        // recording MontoyaTestFactory RequestOptions (mirrors Flow 3's SAME_HOST assertion).
        RequestOptions options = DiscoveryRequestOptions.noRedirect();

        assertThat(recorded(options).redirectionMode()).contains(RedirectionMode.NEVER);
    }
}
