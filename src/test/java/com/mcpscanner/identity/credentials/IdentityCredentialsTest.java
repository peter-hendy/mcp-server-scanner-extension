package com.mcpscanner.identity.credentials;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityOAuthSettings;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.identity.IdentityStore;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import com.mcpscanner.core.oauth.RegistrationMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class IdentityCredentialsTest {

    private static final OAuthClientHints SHARED_OAUTH = new OAuthClientHints(URI.create("https://as.example"),
            List.of("files:read"), "scanner-client", null, false, 8765, Duration.ofSeconds(90),
            RegistrationMode.PRE_REGISTERED, null);

    private final AtomicReference<Optional<OAuthClientHints>> sharedClient = new AtomicReference<>(Optional.empty());
    private IdentityRegistry registry;
    private IdentityCredentials credentials;

    @BeforeEach
    void setUp() {
        registry = new IdentityRegistry(new IdentityStore(InMemoryPersistedObject.configStore(), McpEventLog.noop()));
        credentials = new IdentityCredentials(registry, sharedClient::get);
    }

    @Test
    void oauthWithoutItsOwnIssuerSignsInThroughTheSharedClient() {
        sharedClient.set(Optional.of(SHARED_OAUTH));
        Identity bob = registry.add(identity("bob", IdentityAuthKind.OAUTH));

        assertThat(credentials.requirement(bob.id()))
                .isEqualTo(new CredentialRequirement.BrowserSignInRequired(SHARED_OAUTH));
    }

    @Test
    void theConnectAsUserSignsInThroughTheSharedClientLikeAnyOtherUser() {
        sharedClient.set(Optional.of(SHARED_OAUTH));
        Identity userOne = registry.primaryUser();
        registry.update(userOne.withAuthKind(IdentityAuthKind.OAUTH));

        assertThat(credentials.requirement(userOne.id()))
                .isEqualTo(new CredentialRequirement.BrowserSignInRequired(SHARED_OAUTH));
    }

    @Test
    void aBearerUserAsksForAToken() {
        Identity bob = registry.add(identity("bob", IdentityAuthKind.BEARER));

        assertThat(credentials.requirement(bob.id())).isEqualTo(CredentialRequirement.TokenRequired.INSTANCE);
    }

    @Test
    void aCustomHeaderUserAsksForValuesOfItsHeaders() {
        Identity bob = registry.add(identity("bob", IdentityAuthKind.CUSTOM_HEADERS)
                .withHeaderNames(List.of("X-Api-Key", "X-Tenant")));

        assertThat(credentials.requirement(bob.id()))
                .isEqualTo(new CredentialRequirement.HeaderValuesRequired(List.of("X-Api-Key", "X-Tenant")));
    }

    @Test
    void anOAuthGrantPrimaryIsBlockedByItsGrantSettingsLikeConnectIs() {
        AtomicReference<Optional<String>> blocker = new AtomicReference<>(Optional.of("Configure a client secret"));
        credentials = new IdentityCredentials(registry, new SharedAuthSettings() {
            @Override
            public Optional<OAuthClientHints> oauthClient() {
                return Optional.empty();
            }

            @Override
            public Optional<String> grantBlocker(IdentityAuthKind kind) {
                return kind == IdentityAuthKind.OAUTH_CLIENT_CREDENTIALS ? blocker.get() : Optional.empty();
            }
        });
        Identity userOne = registry.primaryUser();
        registry.update(userOne.withAuthKind(IdentityAuthKind.OAUTH_CLIENT_CREDENTIALS));

        assertThat(credentials.requirement(userOne.id()))
                .isEqualTo(new CredentialRequirement.Unavailable("Configure a client secret"));

        blocker.set(Optional.empty());

        assertThat(credentials.requirement(userOne.id())).isEqualTo(CredentialRequirement.GrantAtConnect.INSTANCE);
    }

    @Test
    void aUserWithAuthNoneNeedsNoCredentials() {
        Identity bob = registry.add(identity("bob", IdentityAuthKind.NONE));

        assertThat(credentials.requirement(bob.id())).isEqualTo(CredentialRequirement.NoCredentials.INSTANCE);
    }

    @Test
    void ownOAuthUsesTheIdentityIssuerAndClient() {
        Identity bob = registry.add(identity("bob", IdentityAuthKind.OAUTH).withOAuth(
                new IdentityOAuthSettings("https://other-as.example", "bob-client", List.of("read"))));

        assertThat(credentials.requirement(bob.id())).isInstanceOfSatisfying(
                CredentialRequirement.BrowserSignInRequired.class, signIn -> {
                    assertThat(signIn.hints().issuer()).isEqualTo(URI.create("https://other-as.example"));
                    assertThat(signIn.hints().clientId()).isEqualTo("bob-client");
                    assertThat(signIn.hints().scopes()).containsExactly("read");
                    assertThat(signIn.hints().registrationMode()).isEqualTo(RegistrationMode.PRE_REGISTERED);
                });
    }

    @Test
    void ownOAuthWithoutAClientRegistersDynamically() {
        Identity bob = registry.add(identity("bob", IdentityAuthKind.OAUTH).withOAuth(
                new IdentityOAuthSettings("https://other-as.example", "", List.of())));

        assertThat(credentials.requirement(bob.id())).isInstanceOfSatisfying(
                CredentialRequirement.BrowserSignInRequired.class, signIn -> {
                    assertThat(signIn.hints().clientId()).isNull();
                    assertThat(signIn.hints().allowDcr()).isTrue();
                });
    }

    @Test
    void oauthWithoutAnIssuerOrASharedClientCannotSignIn() {
        Identity bob = registry.add(identity("bob", IdentityAuthKind.OAUTH));

        assertThat(credentials.requirement(bob.id())).isInstanceOf(CredentialRequirement.Unavailable.class);
    }

    @Test
    void bearerTokenBecomesTheIdentityCredential() {
        Identity bob = registry.add(identity("bob", IdentityAuthKind.BEARER));

        credentials.supplyToken(bob.id(), " bob-token ");

        assertThat(registry.credential(bob.id())).get()
                .satisfies(strategy -> assertThat(strategy.headers()).containsEntry("Authorization", "Bearer bob-token"));
        assertThat(registry.compared()).extracting(Identity::id).containsExactly(bob.id());
    }

    @Test
    void customHeaderValuesBecomeTheIdentityCredential() {
        Identity bob = registry.add(identity("bob", IdentityAuthKind.CUSTOM_HEADERS)
                .withHeaderNames(List.of("X-Api-Key")));

        credentials.supplyHeaderValues(bob.id(), Map.of("X-Api-Key", "bob-key"));

        assertThat(registry.credential(bob.id())).get()
                .satisfies(strategy -> assertThat(strategy.headers()).containsExactly(Map.entry("X-Api-Key", "bob-key")));
    }

    @Test
    void rejectsCredentialsThatDoNotMatchTheRequirement() {
        Identity bob = registry.add(identity("bob", IdentityAuthKind.CUSTOM_HEADERS)
                .withHeaderNames(List.of("X-Api-Key")));
        Identity carol = registry.add(identity("carol", IdentityAuthKind.BEARER));

        assertThatThrownBy(() -> credentials.supplyToken(bob.id(), "token"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> credentials.supplyHeaderValues(bob.id(), Map.of("X-Other", "v")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("X-Api-Key");
        assertThatThrownBy(() -> credentials.supplyHeaderValues(bob.id(), Map.of("X-Api-Key", "a\r\nb")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> credentials.supplyToken(carol.id(), "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(registry.credential(bob.id())).isEmpty();
        assertThat(registry.credential(carol.id())).isEmpty();
    }

    @Test
    void clearingForgetsTheLiveCredential() {
        Identity bob = registry.add(identity("bob", IdentityAuthKind.BEARER));
        credentials.supplyToken(bob.id(), "bob-token");

        credentials.clear(bob.id());

        assertThat(registry.credential(bob.id())).isEmpty();
    }

    @Test
    void theHeldBearerTokenIsReadBackFromTheLiveCredential() {
        Identity bob = registry.add(identity("bob", IdentityAuthKind.BEARER));
        credentials.supplyToken(bob.id(), " bob-token ");

        assertThat(credentials.heldToken(bob.id())).contains("bob-token");
    }

    @Test
    void theHeldHeaderValuesAreReadBackFromTheLiveCredential() {
        Identity bob = registry.add(identity("bob", IdentityAuthKind.CUSTOM_HEADERS)
                .withHeaderNames(List.of("X-Api-Key", "X-Tenant")));
        credentials.supplyHeaderValues(bob.id(), Map.of("X-Api-Key", "bob-key", "X-Tenant", "acme"));

        assertThat(credentials.heldHeaderValues(bob.id()))
                .containsExactly(Map.entry("X-Api-Key", "bob-key"), Map.entry("X-Tenant", "acme"));
    }

    @Test
    void aUserWithoutACredentialHoldsNothing() {
        Identity bob = registry.add(identity("bob", IdentityAuthKind.BEARER));
        Identity carol = registry.add(identity("carol", IdentityAuthKind.CUSTOM_HEADERS)
                .withHeaderNames(List.of("X-Api-Key")));

        assertThat(credentials.heldToken(bob.id())).isEmpty();
        assertThat(credentials.heldHeaderValues(carol.id())).isEmpty();
    }

    @Test
    void aSignedInSessionIsNeverReadAsATypedTokenOrHeaderValues() {
        Identity carol = registry.add(identity("carol", IdentityAuthKind.OAUTH));
        AuthStrategy session = mock(AuthStrategy.class);
        registry.setCredential(carol.id(), session);

        assertThat(credentials.heldToken(carol.id())).isEmpty();
        assertThat(credentials.heldHeaderValues(carol.id())).isEmpty();
        verifyNoInteractions(session);
    }

    private static Identity identity(String name, IdentityAuthKind kind) {
        return Identity.create(name, IdentityRole.PEER, kind);
    }
}
