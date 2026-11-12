package com.mcpscanner.ui.connect;

import com.mcpscanner.config.AuthTypeNames;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityOAuthSettings;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.identity.IdentityStore;
import com.mcpscanner.identity.credentials.CredentialNeed;
import com.mcpscanner.identity.credentials.PrimaryUserCredentials;
import com.mcpscanner.identity.credentials.IdentityCredentials;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrimaryUserAuthSourceTest {

    private final ServerAuthSettings authentication = mock(ServerAuthSettings.class);
    private final OAuthClientHints sharedClient = mock(OAuthClientHints.class);
    private IdentityRegistry registry;
    private PrimaryUserAuthSource source;
    private Identity alice;

    @BeforeEach
    void setUp() {
        registry = new IdentityRegistry(new IdentityStore(InMemoryPersistedObject.configStore(), McpEventLog.noop()));
        source = new PrimaryUserAuthSource(authentication, new PrimaryUserCredentials(registry,
                new IdentityCredentials(registry, () -> Optional.of(sharedClient))));
        alice = registry.add(Identity.create("alice", IdentityRole.PEER, IdentityAuthKind.BEARER));
        registry.makePrimary(alice.id());
    }

    @Test
    void connectRunsAsTheConnectAsUsersAuthKind() {
        assertThat(source.authType()).isEqualTo(AuthTypeNames.BEARER);

        registry.update(alice.withAuthKind(IdentityAuthKind.CUSTOM_HEADERS));
        assertThat(source.authType()).isEqualTo(AuthTypeNames.CUSTOM_HEADERS);

        registry.update(alice.withAuthKind(IdentityAuthKind.OAUTH));
        assertThat(source.authType()).isEqualTo(AuthTypeNames.OAUTH);
    }

    @Test
    void aDetectingPrimaryUserAutoDetectsWhateverOtherUsersHold() {
        registry.update(alice.withAuthKind(IdentityAuthKind.DETECT));
        Identity bob = registry.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        registry.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-token"));

        assertThat(source.authType()).isEqualTo(AuthTypeNames.AUTO);
    }

    @Test
    void aDetectingPrimaryUserWithADetectedSessionReusesIt() {
        registry.update(alice.withAuthKind(IdentityAuthKind.DETECT));
        AuthStrategy session = new BearerTokenAuthStrategy("session");
        registry.setCredential(alice.id(), session);

        assertThat(source.authType()).isEqualTo(AuthTypeNames.OAUTH);
        assertThat(source.signedInSession()).containsSame(session);
    }

    @Test
    void anAuthNonePrimaryUserConnectsWithoutCredentials() {
        registry.update(alice.withAuthKind(IdentityAuthKind.NONE));

        assertThat(source.authType()).isEqualTo(AuthTypeNames.NONE);
    }

    @Test
    void aPrimaryUserSetToAnOAuthGrantConnectsWithThatGrantsSettings() {
        registry.update(alice.withAuthKind(IdentityAuthKind.OAUTH_ENTERPRISE_MANAGED));
        OptionalGrantSettings settings = mock(OptionalGrantSettings.class);
        when(authentication.optionalGrantSettings(OptionalGrantSettings.Profile.ENTERPRISE_MANAGED))
                .thenReturn(settings);

        assertThat(source.authType()).isEqualTo(AuthTypeNames.OAUTH);
        assertThat(source.isOptionalGrantSelected()).isTrue();
        assertThat(source.optionalGrantSettings()).isSameAs(settings);
    }

    @Test
    void anOAuthGrantOnlyAppliesWhileThePrimaryUserIsSetToIt() {
        registry.update(alice.withAuthKind(IdentityAuthKind.OAUTH_CLIENT_CREDENTIALS));
        assertThat(source.isOptionalGrantSelected()).isTrue();

        registry.update(registry.require(alice.id()).withAuthKind(IdentityAuthKind.BEARER));

        assertThat(source.isOptionalGrantSelected()).isFalse();
        assertThat(source.authType()).isEqualTo(AuthTypeNames.BEARER);
    }

    @Test
    void aMissingTokenBlocksConnectNamingTheUser() {
        assertThat(source.connectBlocker()).contains("Enter a bearer token for alice");

        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-token"));

        assertThat(source.connectBlocker()).isEmpty();
    }

    @Test
    void aMissingTokenIsACredentialNeedOfThePrimaryUser() {
        assertThat(source.connectNeed()).contains(
                new CredentialNeed.MissingCredential(IdentityAuthKind.BEARER, "Enter a bearer token for alice"));

        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-token"));

        assertThat(source.connectNeed()).isEmpty();
    }

    @Test
    void anIncompleteSharedOAuthSignInIsACredentialNeedUntilTheUserIsSignedIn() {
        source = new PrimaryUserAuthSource(authentication, new PrimaryUserCredentials(registry,
                new IdentityCredentials(registry, Optional::empty)));
        registry.update(alice.withAuthKind(IdentityAuthKind.OAUTH));
        when(authentication.oauthHints()).thenThrow(new IllegalArgumentException("Enter the OAuth client ID"));

        assertThat(source.connectNeed()).contains(new CredentialNeed.OAuthSignInIncomplete("Enter the OAuth client ID"));

        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("session"));
        assertThat(source.connectNeed()).isEmpty();
    }

    @Test
    void neitherAnAuthNoneUserNorAnOptionalGrantNeedsACredential() {
        registry.update(alice.withAuthKind(IdentityAuthKind.NONE));
        assertThat(source.connectNeed()).isEmpty();

        registry.update(registry.require(alice.id()).withAuthKind(IdentityAuthKind.OAUTH_CLIENT_CREDENTIALS));
        assertThat(source.connectNeed()).isEmpty();
    }

    @Test
    void anAuthNoneUserNeverBlocksConnect() {
        registry.update(alice.withAuthKind(IdentityAuthKind.NONE));

        assertThat(source.connectBlocker()).isEmpty();
    }

    @Test
    void anInvalidSharedClientBlocksAnOAuthUserUntilItIsSignedIn() {
        source = new PrimaryUserAuthSource(authentication, new PrimaryUserCredentials(registry,
                new IdentityCredentials(registry, Optional::empty)));
        registry.update(alice.withAuthKind(IdentityAuthKind.OAUTH));
        when(authentication.oauthHints()).thenThrow(new IllegalArgumentException("Enter the OAuth client ID"));

        assertThat(source.connectBlocker()).contains("Enter the OAuth client ID");

        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("session"));
        assertThat(source.connectBlocker()).isEmpty();
    }

    @Test
    void aSelectedOptionalGrantIsBlockedOnlyByItsOwnSettings() {
        registry.update(alice.withAuthKind(IdentityAuthKind.OAUTH_CLIENT_CREDENTIALS));
        when(authentication.optionalGrantBlocker(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS))
                .thenReturn(Optional.of("Enter the client secret"));

        assertThat(source.connectBlocker()).contains("Enter the client secret");
    }

    @Test
    void theConnectionCredentialIsTheConnectAsUsersNotAnotherUsers() {
        Identity bob = registry.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        AuthStrategy aliceToken = new BearerTokenAuthStrategy("alice-token");
        registry.setCredential(alice.id(), aliceToken);
        registry.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-token"));

        assertThat(source.currentAuthStrategy()).isSameAs(aliceToken);
    }

    @Test
    void aMissingCredentialRefusesTheAttempt() {
        assertThatThrownBy(source::currentAuthStrategy).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alice");
    }

    @Test
    void anOAuthUserSignsInThroughItsOwnClientOrTheSharedOne() {
        registry.update(alice.withAuthKind(IdentityAuthKind.OAUTH));
        assertThat(source.oauthHints()).isSameAs(sharedClient);

        registry.update(registry.require(alice.id()).withOAuth(
                new IdentityOAuthSettings("https://alice-as.example", "alice-client", List.of())));

        assertThat(source.oauthHints().issuer()).isEqualTo(URI.create("https://alice-as.example"));
    }

    @Test
    void aSignedInConnectAsUserOffersItsSession() {
        registry.update(alice.withAuthKind(IdentityAuthKind.OAUTH));
        AuthStrategy session = new BearerTokenAuthStrategy("session");
        registry.setCredential(alice.id(), session);

        assertThat(source.signedInSession()).containsSame(session);
    }
}
