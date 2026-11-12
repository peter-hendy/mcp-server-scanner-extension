package com.mcpscanner.ui.identities;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.testutil.SwingTree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.awt.Component;

import static com.mcpscanner.ui.identities.IdentitiesHarness.fromEdt;
import static com.mcpscanner.ui.identities.IdentitiesHarness.onEdt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Timeout(30)
class SharedCredentialsBannerTest {

    private final IdentitiesHarness harness = new IdentitiesHarness();
    private UsersPanel panel;

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void twoUsersHoldingTheSameTokenAreWarnedInEachEditor() throws Exception {
        Identity user = harness.add("User", IdentityRole.PEER, IdentityAuthKind.BEARER);
        harness.registry.setCredential(harness.userOne().id(), new BearerTokenAuthStrategy("same-token"));
        harness.registry.setCredential(user.id(), new BearerTokenAuthStrategy("same-token"));
        open();

        select(0);
        await().until(() -> fromEdt(() -> showing(banner())));
        assertThat(fromEdt(() -> banner().message())).isEqualTo("Same credentials as " + harness.userOne().name()
                + ": Check access and Scan do not compare users signed in as the same account.");

        await().until(() -> fromEdt(() -> bannerIn(panel.primaryEditor()).message()).equals(
                "Same credentials as User: Check access and Scan do not compare users signed in as the same account."));
    }

    @Test
    void differentTokensShowNoWarningAndChangingOneClearsIt() throws Exception {
        Identity user = harness.add("User", IdentityRole.PEER, IdentityAuthKind.BEARER);
        harness.registry.setCredential(harness.userOne().id(), new BearerTokenAuthStrategy("same-token"));
        harness.registry.setCredential(user.id(), new BearerTokenAuthStrategy("same-token"));
        open();
        select(0);
        await().until(() -> fromEdt(() -> showing(banner())));

        harness.registry.setCredential(user.id(), new BearerTokenAuthStrategy("other-token"));

        await().until(() -> !fromEdt(() -> showing(banner())));
    }

    @Test
    void usersWithoutCredentialsAreNeverWarned() throws Exception {
        harness.add("User", IdentityRole.PEER, IdentityAuthKind.NONE);
        open();

        select(0);

        assertThat(fromEdt(() -> showing(banner()))).isFalse();
    }

    private SharedCredentialsBanner banner() {
        return bannerIn(panel.comparedEditor());
    }

    private static SharedCredentialsBanner bannerIn(IdentityEditor editor) {
        return SwingTree.descendants(editor).stream().filter(SharedCredentialsBanner.class::isInstance)
                .map(SharedCredentialsBanner.class::cast).findFirst().orElseThrow();
    }

    private void open() throws Exception {
        onEdt(() -> panel = new UsersPanel(harness.services, GrantCredentialForms.NONE, () -> { }));
    }

    private void select(int row) throws Exception {
        onEdt(() -> panel.table().setRowSelectionInterval(row, row));
    }

    private static boolean showing(Component component) {
        for (Component c = component; c != null; c = c.getParent()) {
            if (!c.isVisible()) {
                return false;
            }
            if (c instanceof IdentityEditor) {
                return true;
            }
        }
        return true;
    }
}
