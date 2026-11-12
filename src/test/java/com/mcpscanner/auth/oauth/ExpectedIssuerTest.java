package com.mcpscanner.auth.oauth;

import com.nimbusds.oauth2.sdk.as.AuthorizationServerMetadata;
import com.nimbusds.oauth2.sdk.id.Issuer;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpectedIssuerTest {

    private final ExpectedIssuer expected = new ExpectedIssuer(URI.create("https://issuer.example/tenant"));

    @Test
    void matchesOnlyTheExactIssuerString() {
        assertThat(expected.matches("https://issuer.example/tenant")).isTrue();
        assertThat(expected.matches("https://issuer.example/tenant/")).isFalse();
        assertThat(expected.matches("https://ISSUER.example/tenant")).isFalse();
        assertThat(expected.matches((URI) null)).isFalse();
    }

    @Test
    void lenientMatchAcceptsOnlyASingleTrailingSlash() {
        assertThat(expected.matchesAllowingTrailingSlash("https://issuer.example/tenant/")).isTrue();
        assertThat(expected.matchesAllowingTrailingSlash("https://issuer.example/tenant//")).isFalse();
    }

    @Test
    void metadataDeclaringAnotherIssuerIsRejected() {
        var metadata = new AuthorizationServerMetadata(new Issuer("https://attacker.example"));

        assertThatThrownBy(() -> expected.requireDeclaredBy(metadata))
                .isInstanceOf(OAuthException.class)
                .hasMessage("OAuth metadata issuer mismatch");
    }

    @Test
    void metadataDeclaringTheExpectedIssuerIsReturned() {
        var metadata = new AuthorizationServerMetadata(new Issuer("https://issuer.example/tenant"));

        assertThat(expected.requireDeclaredBy(metadata)).isSameAs(metadata);
    }
}
