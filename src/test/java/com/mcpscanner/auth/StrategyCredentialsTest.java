package com.mcpscanner.auth;

import com.mcpscanner.core.connection.RecoveryResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyCredentialsTest {

    @Test
    void sendsTheStrategyHeaders() {
        AuthStrategy bearer = () -> Map.of("Authorization", "Bearer t");

        assertThat(new StrategyCredentials(bearer).headers()).containsExactly(Map.entry("Authorization", "Bearer t"));
    }

    @Test
    void treatsMissingStrategyHeadersAsNone() {
        AuthStrategy absent = () -> null;

        assertThat(new StrategyCredentials(absent).headers()).isEmpty();
    }

    @Test
    void declaresTheHeaderNamesTheStrategyContributes() {
        AuthStrategy custom = () -> Map.of("X-Api-Key", "k");

        assertThat(new StrategyCredentials(custom).headerNames()).containsExactly("X-Api-Key");
    }

    @Test
    void neverRecoversAChallengeByItself() {
        AuthStrategy bearer = () -> Map.of("Authorization", "Bearer t");

        assertThat(new StrategyCredentials(bearer).beginOperation("op").recover(null)).isEqualTo(RecoveryResult.STOP);
    }
}
