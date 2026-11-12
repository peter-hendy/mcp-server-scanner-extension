package com.mcpscanner.ui.identities;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.testutil.SwingTree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static com.mcpscanner.ui.identities.IdentitiesHarness.fromEdt;
import static com.mcpscanner.ui.identities.IdentitiesHarness.onEdt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(30)
class StaleConnectionBannerTest {

    private final IdentitiesHarness harness = new IdentitiesHarness();
    private UsersPanel panel;

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void anotherUsersCredentialWhileAnonymousRaisesNoBanner() throws Exception {
        harness.connectedAs(harness.userOne(), new NoAuthStrategy());
        Identity alice = harness.add("alice", IdentityRole.PEER, IdentityAuthKind.BEARER);
        open();
        onEdt(() -> panel.select(alice.id()));

        harness.registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alpha"));
        onEdt(panel::refresh);

        assertThat(fromEdt(() -> comparedBanner().isVisible())).isFalse();
    }

    @Test
    void theConnectedUsersEditorPointsToTheHeaderReconnectAfterATokenEdit() throws Exception {
        Identity alice = harness.add("alice", IdentityRole.PEER, IdentityAuthKind.BEARER);
        harness.registry.setCredential(alice.id(), new BearerTokenAuthStrategy("alpha"));
        harness.registry.makePrimary(alice.id());
        harness.connectedAs(alice, new BearerTokenAuthStrategy("alpha"));
        open();

        harness.registry.setCredential(alice.id(), new BearerTokenAuthStrategy("beta"));

        await().until(() -> fromEdt(() -> banner().isVisible()));
        assertThat(fromEdt(() -> banner().message()))
                .isEqualTo("alice's credentials changed since you connected. Use Reconnect at the top.");
        assertThat(fromEdt(() -> banner().actionButton())).isNull();
    }

    private StaleConnectionBanner banner() {
        return bannerIn(panel.primaryEditor());
    }

    private StaleConnectionBanner comparedBanner() {
        return bannerIn(panel.comparedEditor());
    }

    private static StaleConnectionBanner bannerIn(IdentityEditor editor) {
        return SwingTree.descendants(editor).stream().filter(StaleConnectionBanner.class::isInstance)
                .map(StaleConnectionBanner.class::cast).findFirst().orElseThrow();
    }

    private void open() throws Exception {
        onEdt(() -> panel = new UsersPanel(harness.services, GrantCredentialForms.NONE, () -> { }));
    }

}
