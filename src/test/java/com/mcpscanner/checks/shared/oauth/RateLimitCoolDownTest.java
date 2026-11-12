package com.mcpscanner.checks.shared.oauth;

import burp.api.montoya.http.message.responses.HttpResponse;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.status;
import static com.mcpscanner.checks.shared.oauth.ScriptedHttp.withHeader;
import static org.assertj.core.api.Assertions.assertThat;

class RateLimitCoolDownTest {

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
    }

    private static Duration coolDownOf(String rawResponse) {
        return RateLimitCoolDown.of(HttpResponse.httpResponse(rawResponse));
    }

    @Test
    void waitsTheRetryAfterSeconds() {
        assertThat(coolDownOf(withHeader(429, "Retry-After", "17"))).isEqualTo(Duration.ofSeconds(17));
    }

    @Test
    void waitsTheDefaultWithoutARetryAfter() {
        assertThat(coolDownOf(status(429))).isEqualTo(RateLimitCoolDown.DEFAULT);
    }

    @Test
    void waitsTheDefaultForARetryAfterDate() {
        assertThat(coolDownOf(withHeader(429, "Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT")))
                .isEqualTo(RateLimitCoolDown.DEFAULT);
    }

    @Test
    void capsAnExcessiveRetryAfter() {
        assertThat(coolDownOf(withHeader(429, "Retry-After", "99999999999999999999")))
                .isEqualTo(RateLimitCoolDown.MAXIMUM);
    }

    @Test
    void waitsAtLeastTheMinimumForAZeroRetryAfter() {
        assertThat(coolDownOf(withHeader(429, "Retry-After", "0"))).isEqualTo(RateLimitCoolDown.MINIMUM);
    }
}
