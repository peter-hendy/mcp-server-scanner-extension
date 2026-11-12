package com.mcpscanner.automation;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.identity.IdentityAuthKind;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandAuthTest {

    @Test
    void aBearerTokenBecomesTheBearerStrategyAUserWouldConfigure() {
        var strategy = new CommandAuth("token-1", null).strategy();

        assertThat(strategy).isInstanceOf(BearerTokenAuthStrategy.class);
        assertThat(strategy.headers()).containsEntry("Authorization", "Bearer token-1");
    }

    @Test
    void headersBecomeTheCustomHeaderStrategy() {
        var strategy = new CommandAuth(null, Map.of("X-Api-Key", "k")).strategy();

        assertThat(strategy).isInstanceOf(CustomHeaderAuthStrategy.class);
        assertThat(strategy.headers()).containsExactly(Map.entry("X-Api-Key", "k"));
    }

    @Test
    void noCredentialMeansNoAuthentication() {
        assertThat(CommandAuth.NONE.strategy()).isInstanceOf(NoAuthStrategy.class);
    }

    @Test
    void namesTheAuthKindAUserWouldPick() {
        assertThat(new CommandAuth("t", null).kind()).isEqualTo(IdentityAuthKind.BEARER);
        assertThat(new CommandAuth(null, Map.of("X-Api-Key", "k")).kind()).isEqualTo(IdentityAuthKind.CUSTOM_HEADERS);
        assertThat(CommandAuth.NONE.kind()).isEqualTo(IdentityAuthKind.NONE);
    }

    @Test
    void rejectsABearerTokenAndHeadersTogether() {
        assertThatThrownBy(() -> new CommandAuth("t", Map.of("X-Api-Key", "k")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
