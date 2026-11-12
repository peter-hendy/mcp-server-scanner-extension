package com.mcpscanner.identity;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionFreshnessTest {

    private IdentityRegistry registry;
    private ConnectionFreshness freshness;
    private Identity userOne;

    @BeforeEach
    void setUp() {
        registry = new IdentityRegistry(new IdentityStore(InMemoryPersistedObject.configStore(), McpEventLog.noop()));
        freshness = new ConnectionFreshness(registry);
        userOne = registry.primaryUser();
    }

    @Test
    void aConnectionWithoutCredentialsIsAnonymous() {
        ConnectedIdentity connected = freshness.snapshot(userOne.id(), new NoAuthStrategy());

        assertThat(connected.anonymous()).isTrue();
        assertThat(connected.name()).isEqualTo("User 1");
        assertThat(connected.identityId()).isEqualTo(userOne.id());
    }

    @Test
    void aConnectionWithAHeldTokenNamesTheUserAndKind() {
        Identity alice = bearerUser("alice", "alpha");
        registry.makePrimary(alice.id());

        ConnectedIdentity connected = freshness.snapshot(alice.id(), new BearerTokenAuthStrategy("alpha"));

        assertThat(connected.anonymous()).isFalse();
        assertThat(connected.name()).isEqualTo("alice");
        assertThat(connected.authKind()).isEqualTo(IdentityAuthKind.BEARER);
    }

    @Test
    void anUnknownAttemptUserFallsBackToTheConnectAsUser() {
        assertThat(freshness.snapshot(null, new NoAuthStrategy()).identityId()).isEqualTo(userOne.id());
    }

    @Test
    void anUnchangedConnectionIsCurrent() {
        ConnectedIdentity connected = freshness.snapshot(userOne.id(), new NoAuthStrategy());

        assertThat(freshness.check(connected)).isEmpty();
    }

    @Test
    void editingTheConnectAsUsersTokenMakesTheConnectionOutOfDate() {
        Identity alice = bearerUser("alice", "alpha");
        registry.makePrimary(alice.id());
        ConnectedIdentity connected = freshness.snapshot(alice.id(), new BearerTokenAuthStrategy("alpha"));

        registry.setCredential(alice.id(), new BearerTokenAuthStrategy("beta"));

        assertThat(freshness.check(connected)).contains(
                new StaleConnection(registry.require(alice.id())));
    }

    @Test
    void changingTheConnectAsUsersAuthKindMakesTheConnectionOutOfDate() {
        ConnectedIdentity connected = freshness.snapshot(userOne.id(), new NoAuthStrategy());

        registry.update(userOne.withAuthKind(IdentityAuthKind.BEARER));

        assertThat(freshness.check(connected)).isPresent();
    }

    @Test
    void anotherUserGainingATokenWhileAnonymousLeavesTheConnectionCurrent() {
        ConnectedIdentity connected = freshness.snapshot(userOne.id(), new NoAuthStrategy());
        bearerUser("carol", "carol-token");

        assertThat(freshness.check(connected)).isEmpty();
    }

    @Test
    void anotherUserGainingATokenLeavesAnAuthenticatedConnectionCurrent() {
        Identity alice = bearerUser("alice", "alpha");
        registry.makePrimary(alice.id());
        ConnectedIdentity connected = freshness.snapshot(alice.id(), new BearerTokenAuthStrategy("alpha"));

        bearerUser("bob", "bob-token");

        assertThat(freshness.check(connected)).isEmpty();
    }

    private Identity bearerUser(String name, String token) {
        Identity user = registry.add(Identity.create(name, IdentityRole.PEER, IdentityAuthKind.BEARER));
        registry.setCredential(user.id(), new BearerTokenAuthStrategy(token));
        return user;
    }
}
