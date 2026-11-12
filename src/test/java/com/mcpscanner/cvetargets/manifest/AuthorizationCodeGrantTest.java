package com.mcpscanner.cvetargets.manifest;

import com.mcpscanner.cvetargets.oauth.ArmBoundOAuthStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationCodeGrantTest {

    @Test
    void producesAStrategyBoundWhenTheArmStarts() {
        AuthSetup auth = new AuthSetup(AuthScheme.OAUTH, "extension login",
                new AuthorizationCodeGrant(null, List.of("openid"), new OAuthClientSetup("extension-dcr", null, null, null)));

        assertThat(auth.strategy()).isInstanceOf(ArmBoundOAuthStrategy.class);
        assertThatThrownBy(() -> auth.strategy().headers()).hasMessageContaining("when the arm starts");
    }

    @Test
    void hasNoStaticToken() {
        assertThatThrownBy(() -> new AuthorizationCodeGrant("", List.of(), null).token())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatesTheClientSetup() {
        assertThat(grant(new OAuthClientSetup("static", null, "s", null)).violations())
                .contains("auth.token.client.clientId is required for static registration");
        assertThat(grant(new OAuthClientSetup("dcr", null, null, null)).violations())
                .contains("auth.token.client.tokenEndpointAuthMethod is required for dcr registration");
        assertThat(grant(new OAuthClientSetup("magic", null, null, null)).violations())
                .anyMatch(violation -> violation.startsWith("auth.token.client.registration must be one of"));
        assertThat(grant(null).violations()).contains("auth.token.client is required");
        assertThat(grant(new OAuthClientSetup("extension-dcr", null, null, null)).violations()).isEmpty();
    }

    @Test
    void requiresAnAbsoluteIssuerPath() {
        assertThat(new AuthorizationCodeGrant("api/auth", List.of(), new OAuthClientSetup("extension-dcr", null, null, null))
                .violations()).contains("auth.token.issuerPath must start with /");
    }

    private static AuthorizationCodeGrant grant(OAuthClientSetup client) {
        return new AuthorizationCodeGrant("/api/auth", List.of(), client);
    }
}
