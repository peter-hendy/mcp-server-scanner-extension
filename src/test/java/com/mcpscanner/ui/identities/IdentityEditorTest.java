package com.mcpscanner.ui.identities;

import com.mcpscanner.auth.AccessToken;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.SignedInTokens;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.testutil.SwingTree;
import com.mcpscanner.ui.widgets.HeaderTablePanel;
import com.mcpscanner.ui.widgets.SecretField;
import com.mcpscanner.ui.widgets.WrappingText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import java.awt.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mcpscanner.ui.identities.IdentitiesHarness.fromEdt;
import static com.mcpscanner.ui.identities.IdentitiesHarness.onEdt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Timeout(30)
class IdentityEditorTest {

    private final IdentitiesHarness harness = new IdentitiesHarness();
    private UsersPanel panel;

    @AfterEach
    void tearDown() {
        harness.close();
    }

    @Test
    void aComparedUserIsTitledAndMeasuredAgainstThePrimaryUser() throws Exception {
        Identity bob = harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER);
        open(bob);

        assertThat(fromEdt(() -> panel.comparedEditor().title())).isEqualTo("Compared user: bob");
        assertThat(texts()).contains("Access vs User 1:");
        assertThat(fromEdt(() -> visible(panel.comparedEditor().roleCombo()))).isTrue();
    }

    @Test
    void anOAuthOrDetectUserLinksToTheOAuthClientButAUserWithItsOwnIssuerOrABearerDoesNot() throws Exception {
        java.util.concurrent.atomic.AtomicInteger opened = new java.util.concurrent.atomic.AtomicInteger();
        Identity carol = harness.add("carol", IdentityRole.PEER, IdentityAuthKind.OAUTH);
        onEdt(() -> {
            panel = new UsersPanel(harness.services, GrantCredentialForms.NONE, opened::incrementAndGet);
            panel.select(carol.id());
        });

        OAuthClientLink comparedLink = fromEdt(() -> panel.comparedEditor().credentials().oauthClientLink());
        assertThat(fromEdt(() -> visible(comparedLink))).isTrue();
        assertThat(fromEdt(() -> visible(panel.primaryEditor().credentials().oauthClientLink()))).isTrue();
        onEdt(() -> comparedLink.link().activate());
        assertThat(opened).hasValue(1);

        harness.registry.update(carol.withOAuth(new com.mcpscanner.identity.IdentityOAuthSettings(
                "https://own.example", "", List.of())));
        flush();
        assertThat(fromEdt(() -> visible(comparedLink))).isFalse();

        harness.registry.update(harness.registry.require(carol.id()).withAuthKind(IdentityAuthKind.BEARER));
        flush();
        assertThat(fromEdt(() -> visible(comparedLink))).isFalse();
    }

    @Test
    void onlyThePrimaryUserIsOfferedTheOAuthGrantsAndShowsTheirFields() throws Exception {
        RecordingGrantForms forms = new RecordingGrantForms();
        Identity bob = harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER);
        onEdt(() -> {
            panel = new UsersPanel(harness.services, forms, () -> { });
            panel.select(bob.id());
        });

        assertThat(fromEdt(() -> panel.primaryEditor().authLabels()))
                .contains("OAuth client credentials", "OAuth enterprise-managed");
        assertThat(fromEdt(() -> panel.comparedEditor().authLabels()))
                .doesNotContain("OAuth client credentials", "OAuth enterprise-managed");

        onEdt(() -> panel.primaryEditor().authCombo().setSelectedItem(IdentityAuthKind.OAUTH_CLIENT_CREDENTIALS));
        flush();

        assertThat(harness.registry.primaryUser().authKind()).isEqualTo(IdentityAuthKind.OAUTH_CLIENT_CREDENTIALS);
        assertThat(forms.shown).isEqualTo(IdentityAuthKind.OAUTH_CLIENT_CREDENTIALS);
        assertThat(fromEdt(() -> panel.primaryEditor().kindHelp())).contains("no browser sign-in");
    }

    private static final class RecordingGrantForms implements GrantCredentialForms {
        private IdentityAuthKind shown;

        @Override
        public void addTo(com.mcpscanner.ui.widgets.LabeledForm form) {
        }

        @Override
        public void show(IdentityAuthKind kind) {
            shown = kind;
        }

        @Override
        public boolean offers(IdentityAuthKind kind) {
            return true;
        }
    }

    @Test
    void theEditorShowsANameAndMarkerChangedOutsideIt() throws Exception {
        open(harness.userOne());

        harness.registry.update(harness.userOne().withName("alice").withPrivateMarker("ALICE-PRIVATE"));
        flush();

        assertThat(fromEdt(() -> panel.primaryEditor().nameField().getText())).isEqualTo("alice");
        assertThat(fromEdt(() -> panel.primaryEditor().markerField().getText())).isEqualTo("ALICE-PRIVATE");
        assertThat(harness.registry.primaryUser().name()).isEqualTo("alice");
    }

    @Test
    void aNameCommittedInTheEditorStillFollowsALaterOutsideRename() throws Exception {
        open(harness.userOne());
        onEdt(() -> {
            panel.primaryEditor().nameField().setText("alice");
            panel.primaryEditor().nameField().postActionEvent();
        });
        flush();

        harness.registry.update(harness.registry.primaryUser().withName("Alice Smith"));
        flush();

        assertThat(fromEdt(() -> panel.primaryEditor().nameField().getText())).isEqualTo("Alice Smith");
    }

    @Test
    void anOutsideChangeNeverOverwritesWhatIsBeingTypedInTheName() throws Exception {
        open(harness.userOne());
        onEdt(() -> panel.primaryEditor().nameField().setText("ali"));

        harness.registry.update(harness.userOne().withPrivateMarker("ALICE-PRIVATE"));
        flush();

        assertThat(fromEdt(() -> panel.primaryEditor().nameField().getText())).isEqualTo("ali");
    }

    @Test
    void thePrimaryUserHasNoAccessFieldNoTitleAndNoMakePrimaryAction() throws Exception {
        harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER);
        open(harness.userOne());

        assertThat(fromEdt(() -> visible(panel.primaryEditor().roleCombo()))).isFalse();
        assertThat(fromEdt(() -> visible(panel.primaryEditor().makePrimaryButton()))).isFalse();
        assertThat(fromEdt(() -> panel.primaryEditor().nameField().getText())).isEqualTo("User 1");
    }

    @Test
    void thePrivateMarkerIsAPlainOptionalRowAndOwnIssuerStaysCollapsed() throws Exception {
        Identity bob = harness.add("bob", IdentityRole.PEER, IdentityAuthKind.OAUTH);
        open(bob);

        assertThat(texts()).contains("Private marker:", "▸ " + OwnIssuerFields.TITLE)
                .noneMatch(text -> text.contains("Owned data") || text.contains("Owned objects"));

        onEdt(() -> panel.comparedEditor().markerField().setText("BOB-ONLY"));
        flush();

        assertThat(harness.registry.require(bob.id()).privateMarker()).contains("BOB-ONLY");
    }

    @Test
    void theRoleHelpNamesBothUsers() throws Exception {
        Identity bob = harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER);
        open(bob);
        assertThat(fromEdt(() -> panel.comparedEditor().roleHelp()))
                .isEqualTo("A finding when bob reaches User 1's private objects.");

        onEdt(() -> panel.comparedEditor().roleCombo().setSelectedItem(IdentityRole.LOWER));
        flush();
        assertThat(fromEdt(() -> panel.comparedEditor().roleHelp()))
                .isEqualTo("A finding when bob runs an operation only User 1 should.");

        onEdt(() -> panel.comparedEditor().roleCombo().setSelectedItem(IdentityRole.HIGHER));
        flush();
        assertThat(fromEdt(() -> panel.comparedEditor().roleHelp()))
                .isEqualTo("A finding when User 1 runs an operation only bob should.");
    }

    @Test
    void theMarkerHelpNamesTheUserAndNoOwnedObjectsAreEdited() throws Exception {
        Identity bob = harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER);
        open(bob);

        assertThat(texts())
                .contains("Text only bob can see. Finding it in another user's reply raises the finding to Firm.")
                .noneMatch(text -> text.contains("Owned objects") || text.contains("Ids bob owns"));
    }

    @Test
    void customHeaderValuesAreMaskedBehindOneEyeAndReMaskedOnSelectionChange() throws Exception {
        Identity bob = harness.add("bob", IdentityRole.PEER, IdentityAuthKind.CUSTOM_HEADERS);
        Identity carol = harness.add("carol", IdentityRole.PEER, IdentityAuthKind.CUSTOM_HEADERS);
        open(bob);
        onEdt(() -> panel.comparedEditor().credentials().headerValues().setHeaders(Map.of("X-Api-Key", "bob-key-123")));

        assertThat(fromEdt(() -> visible(panel.comparedEditor().credentials().headerValues().valuesToggle()))).isTrue();
        assertThat(fromEdt(() -> panel.comparedEditor().credentials().headerValues().valuesRevealed())).isFalse();

        onEdt(() -> panel.comparedEditor().credentials().headerValues().revealValues(true));
        onEdt(() -> panel.select(carol.id()));

        assertThat(fromEdt(() -> panel.comparedEditor().credentials().headerValues().valuesRevealed())).isFalse();
    }

    @Test
    void aHeldBearerTokenIsShownMaskedAndTheEyeRevealsIt() throws Exception {
        Identity alice = harness.add("alice", IdentityRole.PEER, IdentityAuthKind.BEARER);
        harness.credentials.credentials().supplyToken(alice.id(), "alice-token");
        open(alice);
        SecretField token = fromEdt(() -> panel.comparedEditor().credentials().tokenField());

        assertThat(fromEdt(token::value)).isEqualTo("alice-token");
        assertThat(fromEdt(() -> token.textField().echoCharIsSet())).isTrue();

        onEdt(() -> token.reveal(true));

        assertThat(fromEdt(() -> token.textField().echoCharIsSet())).isFalse();
    }

    @Test
    void heldHeaderValuesAreShownMaskedAndTheColumnEyeRevealsThem() throws Exception {
        Identity bob = harness.add("bob", IdentityRole.PEER, IdentityAuthKind.CUSTOM_HEADERS);
        onEdt(() -> harness.registry.update(bob.withHeaderNames(List.of("X-Api-Key"))));
        harness.credentials.credentials().supplyHeaderValues(bob.id(), Map.of("X-Api-Key", "bob-key-123"));
        open(bob);
        HeaderTablePanel headers = fromEdt(() -> panel.comparedEditor().credentials().headerValues());

        assertThat(fromEdt(headers::headers)).containsExactly(Map.entry("X-Api-Key", "bob-key-123"));
        assertThat(fromEdt(() -> shownValue(headers))).isEqualTo("••••••••");

        onEdt(() -> headers.revealValues(true));

        assertThat(fromEdt(() -> shownValue(headers))).isEqualTo("bob-key-123");
    }

    @Test
    void editingAHeldTokenReplacesTheCredential() throws Exception {
        Identity alice = harness.add("alice", IdentityRole.PEER, IdentityAuthKind.BEARER);
        harness.credentials.credentials().supplyToken(alice.id(), "alice-token");
        open(alice);

        onEdt(() -> panel.comparedEditor().credentials().tokenField().setValue("alice-rotated"));
        flush();

        assertThat(harness.credentials.credentials().heldToken(alice.id())).contains("alice-rotated");
    }

    @Test
    void switchingUsersMasksAgainAndShowsTheOtherUsersValues() throws Exception {
        Identity alice = harness.add("alice", IdentityRole.PEER, IdentityAuthKind.BEARER);
        Identity bob = harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER);
        harness.credentials.credentials().supplyToken(alice.id(), "alice-token");
        harness.credentials.credentials().supplyToken(bob.id(), "bob-token");
        open(alice);
        onEdt(() -> panel.comparedEditor().credentials().tokenField().reveal(true));

        onEdt(() -> panel.select(bob.id()));

        assertThat(fromEdt(() -> panel.comparedEditor().credentials().tokenField().value())).isEqualTo("bob-token");
        assertThat(fromEdt(() -> panel.comparedEditor().credentials().tokenField().revealed())).isFalse();
    }

    @Test
    void aUserWithoutACredentialShowsAnEmptyTokenFieldWithAPastePrompt() throws Exception {
        Identity alice = harness.add("alice", IdentityRole.PEER, IdentityAuthKind.BEARER);
        open(alice);
        SecretField token = fromEdt(() -> panel.comparedEditor().credentials().tokenField());

        assertThat(fromEdt(token::value)).isEmpty();
        assertThat(fromEdt(() -> token.textField().getClientProperty("JTextField.placeholderText")))
                .isEqualTo("Paste a bearer token");
    }

    @Test
    void revealedSecretsAreMaskedAgainAfterTheSelectionPassesThroughAnonymous() throws Exception {
        Identity bob = harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER);
        open(bob);
        onEdt(() -> panel.comparedEditor().credentials().tokenField().reveal(true));
        onEdt(() -> panel.comparedEditor().credentials().headerValues().revealValues(true));
        int anonymous = fromEdt(() -> panel.table().getRowCount() - 1);

        onEdt(() -> panel.table().setRowSelectionInterval(anonymous, anonymous));
        onEdt(() -> panel.select(bob.id()));

        assertThat(fromEdt(() -> panel.comparedEditor().credentials().tokenField().revealed())).isFalse();
        assertThat(fromEdt(() -> panel.comparedEditor().credentials().headerValues().valuesRevealed())).isFalse();
    }

    @Test
    void aRevealedTokenStaysRevealedWhileTheSameUserIsEdited() throws Exception {
        Identity bob = harness.add("bob", IdentityRole.PEER, IdentityAuthKind.BEARER);
        open(bob);
        onEdt(() -> panel.comparedEditor().credentials().tokenField().reveal(true));

        onEdt(() -> panel.comparedEditor().credentials().tokenField().setValue("bob-token"));
        flush();

        assertThat(fromEdt(() -> panel.comparedEditor().credentials().tokenField().revealed())).isTrue();
    }

    @Test
    void aSignedInOAuthUserShowsWhoItIsAndItsTokensReadOnlyAndMasked() throws Exception {
        Identity carol = harness.add("carol", IdentityRole.LOWER, IdentityAuthKind.OAUTH);
        harness.registry.setCredential(carol.id(), session(Duration.ofMinutes(54).plusSeconds(30), "refresh-1"));
        open(carol);

        CredentialSection credentials = fromEdt(() -> panel.comparedEditor().credentials());
        assertThat(fromEdt(credentials::signInStatus)).isEqualTo("● Signed in as carol@example.com");
        assertThat(fromEdt(() -> credentials.accessToken().value())).isEqualTo("access-1");
        assertThat(fromEdt(() -> credentials.refreshToken().value())).isEqualTo("refresh-1");
        assertThat(fromEdt(() -> credentials.accessToken().textField().isEditable())).isFalse();
        assertThat(fromEdt(() -> credentials.refreshToken().revealed())).isFalse();
        assertThat(fromEdt(() -> visible(credentials.accessToken()))).isTrue();
        assertThat(fromEdt(credentials::tokenHelp)).isEqualTo("Access token expires in 54 min and renews with the "
                + "refresh token.");
    }

    @Test
    void anOAuthUserThatHasNotSignedInShowsNoTokens() throws Exception {
        Identity carol = harness.add("carol", IdentityRole.LOWER, IdentityAuthKind.OAUTH);
        open(carol);

        assertThat(fromEdt(() -> visible(panel.comparedEditor().credentials().accessToken()))).isFalse();
        assertThat(fromEdt(() -> visible(panel.comparedEditor().credentials().refreshToken()))).isFalse();
    }

    private static AuthStrategy session(Duration lifetime, String refreshToken) {
        AuthStrategy session = mock(AuthStrategy.class);
        when(session.headers()).thenReturn(Map.of("Authorization", "Bearer access-1"));
        when(session.signedInTokens()).thenReturn(Optional.of(new SignedInTokens(Optional.of("carol@example.com"),
                AccessToken.of("access-1", Instant.now().plus(lifetime)), Optional.of(refreshToken))));
        return session;
    }

    private static String shownValue(HeaderTablePanel headers) {
        JTable table = headers.table();
        Component cell = table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
        return ((JLabel) cell).getText();
    }

    private void open(Identity identity) throws Exception {
        onEdt(() -> {
            panel = new UsersPanel(harness.services, GrantCredentialForms.NONE, () -> { });
            panel.select(identity.id());
        });
    }

    private List<String> texts() throws Exception {
        return fromEdt(() -> SwingTree.visibleDescendants(panel).stream()
                .map(IdentityEditorTest::text).filter(text -> !text.isEmpty()).toList());
    }

    private static String text(java.awt.Component component) {
        if (component instanceof JLabel label) {
            return label.getText();
        }
        if (component instanceof javax.swing.AbstractButton button) {
            return button.getText();
        }
        return component instanceof WrappingText wrapping ? wrapping.getText() : "";
    }

    private static void flush() throws Exception {
        onEdt(() -> { });
        onEdt(() -> { });
    }

    private static boolean visible(java.awt.Component component) {
        for (java.awt.Component c = component; c != null; c = c.getParent()) {
            if (!c.isVisible()) {
                return false;
            }
            if (c instanceof IdentityEditor) {
                return true;
            }
        }
        return false;
    }
}
