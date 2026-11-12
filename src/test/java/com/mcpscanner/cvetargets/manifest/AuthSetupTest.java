package com.mcpscanner.cvetargets.manifest;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSetupTest {

    @Test
    void noAuthSendsNoCredential() {
        assertThat(AuthSetup.none().strategy().headers()).isEmpty();
    }

    @Test
    void bearerAndOauthSendTheObtainedToken() {
        assertThat(new AuthSetup(AuthScheme.BEARER, "given", new StaticToken("abc")).strategy().headers())
                .containsEntry("Authorization", "Bearer abc");
        assertThat(new AuthSetup(AuthScheme.OAUTH, "minted", new StaticToken("xyz")).strategy().headers())
                .containsEntry("Authorization", "Bearer xyz");
    }

    @Test
    void headerSchemeSendsTheTokenInTheNamedHeader() {
        assertThat(new AuthSetup(AuthScheme.HEADER, "given", new StaticToken("key"), "x-api-key").strategy().headers())
                .containsExactly(Map.entry("x-api-key", "key"));
    }

    @Test
    void extraHeadersAreSentWithOrWithoutAToken() {
        assertThat(new AuthSetup(AuthScheme.NONE, null, null, Map.of("X-Upstream-Url", "http://u.test"))
                .strategy().headers()).containsExactly(Map.entry("X-Upstream-Url", "http://u.test"));
        assertThat(new AuthSetup(AuthScheme.BEARER, "given", new StaticToken("abc"), Map.of("X-Tenant", "t"))
                .strategy().headers()).containsEntry("X-Tenant", "t").containsEntry("Authorization", "Bearer abc");
    }

    @Test
    void headersDefaultToNone() {
        assertThat(AuthSetup.none().headers()).isEmpty();
        assertThat(new AuthSetup(AuthScheme.NONE, null, null, null, null).headers()).isEmpty();
    }

    @Test
    void armHeadersAddToAndOverrideTheTargetHeaders() {
        AuthSetup target = new AuthSetup(AuthScheme.NONE, null, null, Map.of("X-Token", "t", "X-Url", "a"));

        assertThat(target.including(Map.of("X-Url", "b")).strategy().headers())
                .containsEntry("X-Token", "t").containsEntry("X-Url", "b");
    }
}
