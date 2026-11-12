package com.mcpscanner.ui;

import com.mcpscanner.auth.oauth.OAuthException;
import com.mcpscanner.core.oauth.AuthorizationApproval;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthScopeApprovalTest {
    @Test
    void presentsExactScopesAndIdentityOnEdtAndReturnsExplicitApproval() {
        AtomicReference<String> message = new AtomicReference<>();
        AtomicBoolean shownOnEdt = new AtomicBoolean();
        var approval = new OAuthScopeApproval(URI.create("https://issuer.example"),
                URI.create("https://mcp.example/mcp"), "client", text -> {
                    shownOnEdt.set(SwingUtilities.isEventDispatchThread());
                    message.set(text);
                    return true;
                });

        assertThat(approval.approve(Set.of("write", "read"))).isEqualTo(AuthorizationApproval.APPROVED);
        assertThat(shownOnEdt).isTrue();
        assertThat(message.get()).contains("https://issuer.example", "https://mcp.example/mcp", "client", "read\nwrite");
    }

    @Test
    void rejectedOrClosedDialogDeniesAuthorization() {
        var approval = new OAuthScopeApproval(URI.create("https://issuer.example"),
                URI.create("https://mcp.example/mcp"), "client", ignored -> false);
        assertThat(approval.approve(Set.of("write"))).isEqualTo(AuthorizationApproval.DENIED);
    }

    @Test
    void decliningIsLoggedWithTheScopesAndItsConsequence() {
        java.util.List<String> logged = new java.util.ArrayList<>();
        var approval = new OAuthScopeApproval(URI.create("https://issuer.example"),
                URI.create("https://mcp.example/mcp"), "client", ignored -> false, logged::add);

        approval.approve(Set.of("write", "admin"));

        assertThat(logged).containsExactly(
                "OAuth scope increase declined (admin, write); requests that need these scopes will be rejected");
    }

    @Test
    void authorizationFromEdtFailsWithActionableBackgroundTaskMessage() throws Exception {
        var approval = new OAuthScopeApproval(URI.create("https://issuer.example"),
                URI.create("https://mcp.example/mcp"), "client", ignored -> {
                    throw new AssertionError("Dialog must not launch token flow on EDT");
                });
        SwingUtilities.invokeAndWait(() -> assertThatThrownBy(() -> approval.approve(Set.of("write")))
                .isInstanceOf(OAuthException.class).hasMessageContaining("background task").hasMessageContaining("reconnect"));
    }
}
