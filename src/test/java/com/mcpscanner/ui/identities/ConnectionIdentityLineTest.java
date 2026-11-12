package com.mcpscanner.ui.identities;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static com.mcpscanner.ui.identities.IdentitiesHarness.fromEdt;
import static com.mcpscanner.ui.identities.IdentitiesHarness.onEdt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(30)
class ConnectionIdentityLineTest {

    private final IdentitiesHarness harness = new IdentitiesHarness();
    private ConnectionIdentityLine line;

    @AfterEach
    void tearDown() throws Exception {
        onEdt(() -> line.dispose());
        harness.close();
    }

    @Test
    void theLineIsHiddenWhileDisconnected() throws Exception {
        open();

        assertThat(fromEdt(line::isVisible)).isFalse();
    }

    @Test
    void anAnonymousConnectionNamesThePrimaryUserWithNoCredentialsAndNoButton() throws Exception {
        harness.connectedAs(harness.userOne(), new NoAuthStrategy());
        open();

        assertThat(fromEdt(line::isVisible)).isTrue();
        assertThat(fromEdt(line::identityText)).isEqualTo("Connected as User 1 · no credentials");
        assertThat(fromEdt(() -> line.actionButton().isVisible())).isFalse();
        assertThat(fromEdt(line::identityTooltip)).isNull();
    }

    @Test
    void aTokenConnectionNamesTheUserAndAuthKind() throws Exception {
        Identity alice = bearerUser("alice", "alpha");
        harness.connectedAs(alice, new BearerTokenAuthStrategy("alpha"));
        open();

        assertThat(fromEdt(line::identityText)).isEqualTo("Connected as alice · Bearer token");
        assertThat(fromEdt(() -> line.actionButton().isVisible())).isFalse();
    }

    @Test
    void editingTheConnectedUsersTokenOffersReconnect() throws Exception {
        Identity alice = bearerUser("alice", "alpha");
        harness.connectedAs(alice, new BearerTokenAuthStrategy("alpha"));
        open();

        harness.registry.setCredential(alice.id(), new BearerTokenAuthStrategy("beta"));

        await().until(() -> fromEdt(() -> line.actionButton().isVisible()));
        assertThat(fromEdt(() -> line.actionButton().getText())).isEqualTo("Reconnect");
        assertThat(fromEdt(() -> line.actionButton().getToolTipText()))
                .isEqualTo("alice's credentials changed since you connected");
        assertThat(fromEdt(line::identityTooltip)).isEqualTo("alice's credentials changed since you connected");
    }

    @Test
    void anotherUserGainingATokenWhileAnonymousOffersNothing() throws Exception {
        harness.connectedAs(harness.userOne(), new NoAuthStrategy());
        open();

        bearerUser("alice", "alpha");
        onEdt(line::refresh);

        assertThat(fromEdt(() -> line.actionButton().isVisible())).isFalse();
    }

    @Test
    void theButtonHidesAgainOnceTheChangeIsUndone() throws Exception {
        Identity alice = bearerUser("alice", "alpha");
        harness.connectedAs(alice, new BearerTokenAuthStrategy("alpha"));
        open();
        harness.registry.setCredential(alice.id(), new BearerTokenAuthStrategy("beta"));
        await().until(() -> fromEdt(() -> line.actionButton().isVisible()));

        harness.registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alpha"));

        await().until(() -> !fromEdt(() -> line.actionButton().isVisible()));
    }

    @Test
    void reconnectConfirmsAndReconnectsAsTheSameUser() throws Exception {
        Identity alice = bearerUser("alice", "alpha");
        harness.registry.makePrimary(alice.id());
        harness.connectedAs(alice, new BearerTokenAuthStrategy("alpha"));
        open();
        harness.registry.setCredential(alice.id(), new BearerTokenAuthStrategy("beta"));
        await().until(() -> fromEdt(() -> line.actionButton().isVisible()));

        onEdt(() -> line.actionButton().doClick());

        assertThat(harness.reconnectPrompts).containsExactly("alice -> alice");
        assertThat(harness.reconnects).hasValue(1);
        assertThat(harness.registry.primaryUser().id()).isEqualTo(alice.id());
    }

    @Test
    void decliningTheConfirmationLeavesTheConnectionAlone() throws Exception {
        Identity alice = bearerUser("alice", "alpha");
        harness.registry.makePrimary(alice.id());
        harness.connectedAs(alice, new BearerTokenAuthStrategy("alpha"));
        harness.confirmReconnect.set(false);
        open();
        harness.registry.setCredential(alice.id(), new BearerTokenAuthStrategy("beta"));
        await().until(() -> fromEdt(() -> line.actionButton().isVisible()));

        onEdt(() -> line.actionButton().doClick());

        assertThat(harness.reconnects).hasValue(0);
    }

    private Identity bearerUser(String name, String token) {
        Identity user = harness.add(name, IdentityRole.PEER, IdentityAuthKind.BEARER);
        harness.registry.setCredential(user.id(), new BearerTokenAuthStrategy(token));
        return user;
    }

    private void open() throws Exception {
        onEdt(() -> line = new UsersPanel(harness.services, GrantCredentialForms.NONE, () -> { }).connectionIdentityLine());
    }
}
