package com.mcpscanner.identity.credentials;

import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.auth.oauth.OAuthClientHints;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.identity.IdentityStore;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PrimaryUserCredentialsTest {

    private static final OAuthClientHints SHARED_OAUTH = new OAuthClientHints(URI.create("https://as.example"),
            List.of(), "client", null, false, 0, Duration.ofSeconds(30));

    private IdentityRegistry registry;
    private PrimaryUserCredentials connectAs;
    private Identity alice;
    private Identity bob;

    @BeforeEach
    void setUp() {
        registry = new IdentityRegistry(new IdentityStore(InMemoryPersistedObject.configStore(), McpEventLog.noop()));
        connectAs = new PrimaryUserCredentials(registry,
                new IdentityCredentials(registry, () -> Optional.of(SHARED_OAUTH)));
        alice = registry.add(Identity.create("alice", IdentityRole.PEER, IdentityAuthKind.BEARER));
        bob = registry.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        registry.makePrimary(alice.id());
    }

    @Test
    void connectUsesTheConnectAsUsersLiveCredential() {
        BearerTokenAuthStrategy aliceToken = new BearerTokenAuthStrategy("alice-token");
        registry.setCredential(alice.id(), aliceToken);
        registry.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-token"));

        assertThat(connectAs.connectionCredential()).isSameAs(aliceToken);
        assertThat(connectAs.user()).isEqualTo(alice);
    }

    @Test
    void switchingConnectAsSwitchesTheConnectionCredential() {
        BearerTokenAuthStrategy bobToken = new BearerTokenAuthStrategy("bob-token");
        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-token"));
        registry.setCredential(bob.id(), bobToken);

        registry.makePrimary(bob.id());

        assertThat(connectAs.connectionCredential()).isSameAs(bobToken);
    }

    @Test
    void aUserWithAuthNoneConnectsWithoutCredentials() {
        registry.update(alice.withAuthKind(IdentityAuthKind.NONE));

        assertThat(connectAs.connectionCredential().headers()).isEmpty();
    }

    @Test
    void aMissingTokenOrHeaderValuesRefusesToConnectNamingTheUser() {
        assertThatThrownBy(connectAs::connectionCredential).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Enter a bearer token for alice");

        registry.update(alice.withAuthKind(IdentityAuthKind.CUSTOM_HEADERS).withHeaderNames(List.of("X-Key")));

        assertThatThrownBy(connectAs::connectionCredential).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Enter the header values for alice");
    }

    @Test
    void anOAuthUserWithoutASessionSignsInThroughItsClient() {
        registry.update(alice.withAuthKind(IdentityAuthKind.OAUTH));

        assertThat(connectAs.signInClient()).contains(SHARED_OAUTH);
        assertThat(connectAs.liveCredential()).isEmpty();
    }

    @Test
    void aSessionSignedInByConnectIsReportedSoAnEarlierSignInFailureClears() {
        List<String> reported = new ArrayList<>();
        PrimaryUserCredentials reporting = new PrimaryUserCredentials(registry,
                new IdentityCredentials(registry, () -> Optional.of(SHARED_OAUTH)), reported::add);

        reporting.signedIn(alice.id(), mock(OAuthAuthCodeStrategy.class));

        assertThat(reported).containsExactly(alice.id());
    }

    @Test
    void aSignedInSessionBecomesTheConnectAsUsersOAuthCredential() {
        AuthStrategy session = mock(OAuthAuthCodeStrategy.class);

        connectAs.signedIn(alice.id(), session);

        assertThat(registry.primaryUser().authKind()).isEqualTo(IdentityAuthKind.OAUTH);
        assertThat(connectAs.liveCredential()).containsSame(session);
    }

    @Test
    void aSessionSignedInBeforeAConnectAsSwitchStaysWithTheUserItWasSignedInFor() {
        AuthStrategy aliceSession = mock(OAuthAuthCodeStrategy.class);
        registry.makePrimary(bob.id());

        connectAs.signedIn(alice.id(), aliceSession);

        assertThat(registry.require(alice.id()).authKind()).isEqualTo(IdentityAuthKind.OAUTH);
        assertThat(registry.credential(alice.id())).containsSame(aliceSession);
        assertThat(registry.require(bob.id()).authKind()).isEqualTo(IdentityAuthKind.BEARER);
        assertThat(registry.credential(bob.id())).isEmpty();
    }

    @Test
    void aSessionSignedInForARemovedUserIsDropped() {
        registry.makePrimary(bob.id());
        registry.remove(alice.id());

        connectAs.signedIn(alice.id(), mock(OAuthAuthCodeStrategy.class));

        assertThat(registry.credential(bob.id())).isEmpty();
        assertThat(registry.require(bob.id()).authKind()).isEqualTo(IdentityAuthKind.BEARER);
    }

    @Test
    void autoDetectsWheneverThePrimaryUserDetectsAutomaticallyWhateverOtherUsersHold() {
        assertThat(connectAs.autoDetects()).isFalse();

        registry.update(alice.withAuthKind(IdentityAuthKind.DETECT));
        registry.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-token"));

        assertThat(connectAs.autoDetects()).isTrue();
    }

    @Test
    void anAuthNonePrimaryUserNeverAutoDetects() {
        registry.update(alice.withAuthKind(IdentityAuthKind.NONE));

        assertThat(connectAs.autoDetects()).isFalse();
        assertThat(connectAs.connectionCredential()).isInstanceOf(NoAuthStrategy.class);
    }

    @Test
    void detectingNoAuthenticationRecordsTheOutcomeWithoutChangingTheChosenKind() {
        registry.update(alice.withAuthKind(IdentityAuthKind.DETECT));

        connectAs.detectedNoAuthentication(alice.id());

        assertThat(registry.require(alice.id()).authKind()).isEqualTo(IdentityAuthKind.DETECT);
        assertThat(registry.detected(alice.id())).contains(IdentityAuthKind.NONE);
    }

    @Test
    void detectingOAuthKeepsTheChosenKindAndReusesTheSessionOnTheNextConnect() {
        registry.update(alice.withAuthKind(IdentityAuthKind.DETECT));
        AuthStrategy session = mock(OAuthAuthCodeStrategy.class);

        connectAs.signedIn(alice.id(), session);

        assertThat(registry.require(alice.id()).authKind()).isEqualTo(IdentityAuthKind.DETECT);
        assertThat(registry.detected(alice.id())).contains(IdentityAuthKind.OAUTH);
        assertThat(connectAs.autoDetects()).isFalse();
        assertThat(connectAs.connectionCredential()).isSameAs(session);
        assertThat(connectAs.signInClient()).contains(SHARED_OAUTH);
    }

    @Test
    void aRejectedSessionIsForgottenSoTheNextConnectSignsInAgain() {
        connectAs.signedIn(alice.id(), mock(OAuthAuthCodeStrategy.class));

        connectAs.sessionRejected();

        assertThat(connectAs.liveCredential()).isEmpty();
    }
}
