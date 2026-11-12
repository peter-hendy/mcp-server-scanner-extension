package com.mcpscanner.auth;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentHeadersTest {

    @Test
    void aStaticCredentialsCurrentHeadersAreItsHeaders() {
        assertThat(new BearerTokenAuthStrategy("t").currentHeaders()).isEqualTo(Map.of("Authorization", "Bearer t"));
    }

    @Test
    void aRefreshingCredentialsCurrentHeadersCarryItsCurrentTokenWithoutRefreshing() {
        assertThat(refreshing(Optional.of(new AccessToken("held", Optional.empty()))).currentHeaders())
                .isEqualTo(Map.of("Authorization", "Bearer held"));
        assertThat(refreshing(Optional.empty()).currentHeaders()).isEmpty();
    }

    private static AuthStrategy refreshing(Optional<AccessToken> token) {
        return new AuthStrategy() {
            @Override
            public Map<String, String> headers() {
                throw new AssertionError("headers() may refresh over the network");
            }

            @Override
            public boolean supportsRefresh() {
                return true;
            }

            @Override
            public Optional<AccessToken> currentToken() {
                return token;
            }
        };
    }
}
