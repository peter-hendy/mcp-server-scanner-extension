package com.mcpscanner.ui.identities;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.testutil.SwingTree;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.swing.JMenuItem;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mcpscanner.ui.identities.IdentitiesHarness.fromEdt;
import static com.mcpscanner.ui.identities.IdentitiesHarness.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

@Timeout(30)
class PrimaryUserPickerTest {

    private final IdentitiesHarness harness = new IdentitiesHarness();
    private final AtomicInteger usersOpened = new AtomicInteger();
    private PrimaryUserPicker picker;

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void namesThePrimaryUserAndItsAuth() throws Exception {
        open();

        assertThat(fromEdt(picker::getText)).isEqualTo("User 1 · Detect automatically ▾");

        harness.registry.recordDetected(harness.userOne().id(), IdentityAuthKind.NONE);
        onEdt(picker::refresh);
        assertThat(fromEdt(picker::getText)).isEqualTo("User 1 · None (detected) ▾");

        harness.registry.update(harness.userOne().withAuthKind(IdentityAuthKind.OAUTH));
        onEdt(picker::refresh);
        assertThat(fromEdt(picker::getText)).isEqualTo("User 1 · OAuth sign-in ▾");
    }

    @Test
    void theMenuListsEveryUserThenEditUsersAndTheSwitchNote() throws Exception {
        Identity bob = harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER);
        harness.registry.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-token"));
        harness.add("reader", IdentityRole.LOWER, IdentityAuthKind.BEARER);
        open();

        List<JMenuItem> items = fromEdt(this::items);

        assertThat(items).extracting(JMenuItem::getText).containsExactly("User 1 · Detect", "bob · Bearer",
                "reader · Bearer", PrimaryUserPicker.EDIT_USERS, PrimaryUserPicker.SWITCH_NOTE);
        assertThat(items.get(0).isSelected()).isTrue();
        assertThat(items.get(2).isEnabled()).isFalse();
        assertThat(items.get(2).getToolTipText()).isEqualTo("Enter a bearer token for reader first");
        assertThat(items.get(4).isEnabled()).isFalse();
    }

    @Test
    void pickingAUserMakesItPrimaryAndEditUsersOpensTheUsersTab() throws Exception {
        Identity bob = harness.add("bob", IdentityRole.PEER, IdentityAuthKind.NONE);
        open();

        onEdt(() -> item("bob").doClick());
        onEdt(() -> item(PrimaryUserPicker.EDIT_USERS).doClick());

        assertThat(harness.registry.isPrimary(bob.id())).isTrue();
        assertThat(usersOpened).hasValue(1);
    }

    private List<JMenuItem> items() {
        return SwingTree.descendants(picker.menu()).stream().filter(JMenuItem.class::isInstance)
                .map(JMenuItem.class::cast).toList();
    }

    private JMenuItem item(String prefix) {
        return items().stream().filter(item -> item.getText().startsWith(prefix)).findFirst().orElseThrow();
    }

    private void open() throws Exception {
        onEdt(() -> picker = new PrimaryUserPicker(harness.services, usersOpened::incrementAndGet));
    }
}
