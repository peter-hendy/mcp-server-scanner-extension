package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationSessionTest {

    private static final URI MCP = URI.create("https://mcp.example.com/mcp");
    private static final URI SAME_ORIGIN_AUTHORIZE = URI.create("https://mcp.example.com/authorize");

    private static Map<String, String> headers(URI authorize, String baseCookie, Map<String, String> authHeaders) {
        return AuthorizationSession.headersFor(authorize, MCP, baseCookie, () -> authHeaders);
    }

    @Test
    void neverAsksTheAuthStrategyForHeadersForAnotherOrigin() {
        assertThat(AuthorizationSession.headersFor(URI.create("https://auth.example.com/authorize"), MCP, "sid=1",
                () -> {
                    throw new AssertionError("auth strategy consulted");
                })).isEmpty();
    }

    @Test
    void sendsNoCookiesToAnotherOrigin() {
        assertThat(headers(URI.create("https://auth.example.com/authorize"), "sid=1", Map.of("Cookie", "t=2")))
                .isEmpty();
    }

    @Test
    void treatsAnotherPortAsAnotherOrigin() {
        assertThat(headers(URI.create("https://mcp.example.com:8443/authorize"), "sid=1", Map.of())).isEmpty();
    }

    @Test
    void treatsAnExplicitDefaultPortAsTheSameOrigin() {
        assertThat(headers(URI.create("https://MCP.example.com:443/authorize"), "sid=1", Map.of()))
                .containsExactly(Map.entry("Cookie", "sid=1"));
    }

    @Test
    void forwardsTheBaseRequestCookies() {
        assertThat(headers(SAME_ORIGIN_AUTHORIZE, "sid=1; theme=dark", Map.of()))
                .containsExactly(Map.entry("Cookie", "sid=1; theme=dark"));
    }

    @Test
    void mergesTheAuthStrategyCookieHeaderWhateverItsCase() {
        assertThat(headers(SAME_ORIGIN_AUTHORIZE, "sid=1", Map.of("cookie", "token=2")))
                .containsExactly(Map.entry("Cookie", "sid=1; token=2"));
    }

    @Test
    void ignoresNonCookieAuthHeaders() {
        assertThat(headers(SAME_ORIGIN_AUTHORIZE, null, Map.of("Authorization", "Bearer x"))).isEmpty();
    }

    @Test
    void dedupesRepeatedCookies() {
        assertThat(headers(SAME_ORIGIN_AUTHORIZE, "sid=1;sid=1", Map.of("Cookie", "sid=1; token=2")))
                .containsExactly(Map.entry("Cookie", "sid=1; token=2"));
    }

    @Test
    void dropsFragmentsThatAreNotNameValuePairs() {
        assertThat(headers(SAME_ORIGIN_AUTHORIZE, "garbage; ; sid=1", Map.of()))
                .containsExactly(Map.entry("Cookie", "sid=1"));
    }

    @Test
    void sendsNothingWithoutAnyCookie() {
        assertThat(headers(SAME_ORIGIN_AUTHORIZE, null, Map.of())).isEmpty();
    }
}
