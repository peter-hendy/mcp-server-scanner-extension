package com.mcpscanner.ui.identities;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.swing.JButton;
import javax.swing.JPanel;

import static com.mcpscanner.ui.identities.IdentitiesHarness.fromEdt;
import static com.mcpscanner.ui.identities.IdentitiesHarness.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class PrimaryUserChoiceTest {

    private final IdentitiesHarness harness = new IdentitiesHarness();
    private UsersPanel panel;

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void makingAUserPrimaryWhileDisconnectedSwitchesWithoutAsking() throws Exception {
        Identity bob = withToken(harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        open();

        makePrimary(bob);

        assertThat(harness.registry.primaryUser().id()).isEqualTo(bob.id());
        assertThat(harness.reconnectPrompts).isEmpty();
        assertThat(harness.reconnects).hasValue(0);
    }

    @Test
    void makingAUserPrimaryWhileConnectedAsksAndReconnectsAsThatUser() throws Exception {
        Identity bob = withToken(harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        harness.connected.set(true);
        open();

        makePrimary(bob);

        assertThat(harness.reconnectPrompts).containsExactly("User 1 -> bob");
        assertThat(harness.registry.primaryUser().id()).isEqualTo(bob.id());
        assertThat(harness.reconnects).hasValue(1);
        assertThat(harness.registry.find(harness.named("User 1").id())).isPresent();
    }

    @Test
    void theSwitchHappensInsideTheReconnectSoTheConnectionCanStopItsWorkFirst() throws Exception {
        Identity bob = withToken(harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        harness.connected.set(true);
        open();

        makePrimary(bob);

        assertThat(harness.connectAsWhenReconnectStarted).containsExactly("User 1");
        assertThat(harness.registry.primaryUser().name()).isEqualTo("bob");
    }

    @Test
    void makingAUserPrimaryWhileConnectingAsksAndRestartsTheConnection() throws Exception {
        Identity bob = withToken(harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        harness.connecting.set(true);
        open();

        makePrimary(bob);

        assertThat(harness.reconnectPrompts).containsExactly("User 1 -> bob");
        assertThat(harness.reconnects).hasValue(1);
    }

    @Test
    void cancellingTheReconnectKeepsTheCurrentUser() throws Exception {
        Identity bob = withToken(harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        Identity userOne = harness.userOne();
        harness.connected.set(true);
        harness.confirmReconnect.set(false);
        open();

        makePrimary(bob);

        assertThat(harness.reconnectPrompts).hasSize(1);
        assertThat(harness.registry.primaryUser().id()).isEqualTo(userOne.id());
        assertThat(harness.reconnects).hasValue(0);
    }

    @Test
    void aUserWithoutItsCredentialsCannotBeMadePrimaryAndTheButtonSaysWhy() throws Exception {
        Identity bob = harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER);
        open();
        onEdt(() -> panel.select(bob.id()));

        JButton button = fromEdt(() -> panel.comparedEditor().makePrimaryButton());
        assertThat(fromEdt(button::isEnabled)).isFalse();
        assertThat((String) fromEdt(button::getToolTipText)).isEqualTo("Enter a bearer token for bob first");

        onEdt(() -> panel.comparedEditor().credentials().tokenField().setValue("bob-token"));
        flush();
        assertThat(fromEdt(button::isEnabled)).isTrue();
    }

    @Test
    void usersThatNeedNoCredentialsOrSignInOnConnectCanBeMadePrimary() throws Exception {
        Identity guest = harness.add("guest", IdentityRole.PEER, IdentityAuthKind.NONE);
        Identity carol = harness.add("carol", IdentityRole.PEER, IdentityAuthKind.OAUTH);
        open();

        onEdt(() -> panel.select(guest.id()));
        assertThat(fromEdt(() -> panel.comparedEditor().makePrimaryButton().isEnabled())).isTrue();
        onEdt(() -> panel.select(carol.id()));
        assertThat(fromEdt(() -> panel.comparedEditor().makePrimaryButton().isEnabled())).isTrue();
    }

    private Identity withToken(Identity identity) {
        harness.registry.setCredential(identity.id(), new BearerTokenAuthStrategy(identity.name() + "-token"));
        return identity;
    }

    private void makePrimary(Identity identity) throws Exception {
        onEdt(() -> panel.select(identity.id()));
        onEdt(() -> panel.comparedEditor().makePrimaryButton().doClick());
        flush();
    }

    private void open() throws Exception {
        onEdt(() -> panel = new UsersPanel(harness.services, GrantCredentialForms.NONE, () -> { }));
    }

    private static void flush() throws Exception {
        onEdt(() -> { });
        onEdt(() -> { });
    }
}
