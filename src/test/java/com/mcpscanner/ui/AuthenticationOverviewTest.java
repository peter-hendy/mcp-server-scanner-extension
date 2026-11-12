package com.mcpscanner.ui;

import com.mcpscanner.auth.AccessToken;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.SignedInTokens;
import com.mcpscanner.ui.widgets.CopyableSecret;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticationOverviewTest {

    private final Clipboard clipboard = new Clipboard("test");
    private final CopyableSecret refreshToken = new CopyableSecret("refresh token", () -> clipboard);

    @Test
    void showsTheConnectedUserTheTokenAndClientManagementWithACopyableManagementToken() throws Exception {
        AtomicReference<AuthenticationOverview> shown = new AtomicReference<>();
        ClientManagementView management = new ClientManagementView(() -> clipboard);

        SwingUtilities.invokeAndWait(() -> {
            AuthenticationOverview overview = overview(management);
            overview.show(AuthenticationSummary.builder("OAuth 2.1 · Authorization code")
                    .connectedAs("test")
                    .credential(new BearerTokenAuthStrategy("access-1"))
                    .clientManagement(Optional.of(new DcrRegistrations.Registration("mgmt-token",
                            "https://mcp.example.com/register/abc")))
                    .build());
            shown.set(overview);
        });

        assertThat(shown.get().grid().rowCount()).isEqualTo(4);
        assertThat(management.clientUri()).isEqualTo("https://mcp.example.com/register/abc");
        SwingUtilities.invokeAndWait(() -> management.copyButton().doClick());
        assertThat(clipboard.getData(DataFlavor.stringFlavor)).isEqualTo("mgmt-token");
    }

    @Test
    void aHeldRefreshTokenGetsItsOwnMaskedCopyableRowAndTheBlockStaysNarrow() throws Exception {
        AuthStrategy session = mock(AuthStrategy.class);
        when(session.currentToken()).thenReturn(Optional.of(AccessToken.of("access-1", Instant.now().plusSeconds(600))));
        when(session.signedInTokens()).thenReturn(Optional.of(new SignedInTokens(Optional.empty(),
                AccessToken.of("access-1", Instant.now().plusSeconds(600)), Optional.of("refresh-1"))));
        AtomicReference<AuthenticationOverview> shown = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            AuthenticationOverview overview = overview(new ClientManagementView(() -> clipboard));
            overview.show(AuthenticationSummary.builder("OAuth 2.1").credential(session).build());
            overview.setSize(1500, 400);
            overview.doLayout();
            overview.getComponent(0).doLayout();
            shown.set(overview);
        });

        assertThat(refreshToken.isVisible()).isTrue();
        assertThat(refreshToken.secret().revealed()).isFalse();
        assertThat(shown.get().grid().rowCount()).isEqualTo(3);
        assertThat(shown.get().grid().getWidth()).isLessThanOrEqualTo(AuthenticationOverview.MAX_WIDTH);
        SwingUtilities.invokeAndWait(() -> refreshToken.copyButton().doClick());
        assertThat(clipboard.getData(DataFlavor.stringFlavor)).isEqualTo("refresh-1");
    }

    @Test
    void bothTokenRowsShareOneLayoutAndRefreshSitsBesideTheExpiry() throws Exception {
        AuthStrategy session = renewableSession();
        AtomicReference<AuthenticationOverview> shown = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            AuthenticationOverview overview = overview(new ClientManagementView(() -> clipboard));
            overview.show(AuthenticationSummary.builder("OAuth 2.1").credential(session).build());
            overview.setSize(580, 400);
            layOut(overview);
            shown.set(overview);
        });

        CopyableSecret access = (CopyableSecret) com.mcpscanner.testutil.SwingTree.descendants(shown.get()).stream()
                .filter(CopyableSecret.class::isInstance).filter(row -> row != refreshToken).findFirst().orElseThrow();
        assertThat(access.secret().getWidth()).isEqualTo(refreshToken.secret().getWidth());
        assertThat(access.copyButton().getY()).isEqualTo(access.secret().getY());
        assertThat(refreshToken.copyButton().getY()).isEqualTo(refreshToken.secret().getY());
        javax.swing.JButton refresh = AuthUiLookup.button(shown.get(), "Refresh");
        assertThat(javax.swing.SwingUtilities.isDescendingFrom(refresh, access)).isFalse();
        assertThat(java.util.Arrays.asList(refresh.getParent().getComponents()))
                .anyMatch(com.mcpscanner.ui.widgets.WrappingText.class::isInstance);
    }

    private static AuthStrategy renewableSession() {
        AuthStrategy session = mock(AuthStrategy.class);
        AccessToken token = AccessToken.of("access-1", Instant.now().plusSeconds(600));
        when(session.currentToken()).thenReturn(Optional.of(token));
        when(session.supportsRefresh()).thenReturn(true);
        when(session.signedInTokens()).thenReturn(Optional.of(new SignedInTokens(Optional.empty(), token,
                Optional.of("refresh-1"))));
        return session;
    }

    private static void layOut(java.awt.Container container) {
        container.doLayout();
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) {
                layOut(nested);
            }
        }
    }

    @Test
    void withoutARegistrationThereIsNoClientManagementRow() throws Exception {
        AtomicReference<AuthenticationOverview> shown = new AtomicReference<>();
        ClientManagementView management = new ClientManagementView(() -> clipboard);

        SwingUtilities.invokeAndWait(() -> {
            AuthenticationOverview overview = overview(management);
            overview.show(AuthenticationSummary.builder("None").connectedAs("User 1").build());
            shown.set(overview);
        });

        assertThat(management.isVisible()).isFalse();
        assertThat(shown.get().grid().rowCount()).isEqualTo(2);
    }

    private AuthenticationOverview overview(ClientManagementView management) {
        return new AuthenticationOverview(Clock.systemUTC(),
                new AccessTokenView(new TokenRenewal(task -> { }, new TokenRenewal.Reporter() {
                    @Override
                    public void renewed() {
                    }

                    @Override
                    public void failed(String reason) {
                    }
                }), () -> clipboard), refreshToken, management);
    }
}
