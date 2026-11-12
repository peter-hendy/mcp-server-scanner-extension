package com.mcpscanner.ui;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.config.AuthTypeNames;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.awt.event.FocusEvent;

import static com.mcpscanner.ui.McpScannerTabFixture.ENDPOINT;
import static com.mcpscanner.ui.McpScannerTabFixture.awaitConnectLabel;
import static com.mcpscanner.ui.McpScannerTabFixture.awaitDisconnected;
import static com.mcpscanner.ui.McpScannerTabFixture.click;
import static com.mcpscanner.ui.McpScannerTabFixture.connectButton;
import static com.mcpscanner.ui.McpScannerTabFixture.emptyInventory;
import static com.mcpscanner.ui.McpScannerTabFixture.endpointField;
import static com.mcpscanner.ui.McpScannerTabFixture.fromEdt;
import static com.mcpscanner.ui.McpScannerTabFixture.onEdt;
import static com.mcpscanner.ui.McpScannerTabFixture.statusText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EndpointOriginCredentialsTest {

    private static final String OTHER_ORIGIN_ENDPOINT = "https://other.example.test/mcp";
    private static final String ALICE_TOKEN = "Bearer alice-token";

    private McpScannerTabFixture fixture;
    private Identity alice;
    private Identity bob;
    private McpScannerTab tab;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new McpScannerTabFixture();
        when(fixture.clientManager.connect(any())).thenReturn(emptyInventory());
        alice = fixture.identities.primaryUser();
        fixture.useAuth(AuthTypeNames.BEARER);
        alice = fixture.identities.require(alice.id());
        bob = fixture.identities.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        tab = fromEdt(fixture::newTab);
        onEdt(() -> endpointField(tab).setText(ENDPOINT));
        holdTokens();
        onEdt(() -> click(connectButton(tab)));
        awaitConnectLabel(tab, "Disconnect");
        onEdt(() -> click(connectButton(tab)));
        awaitDisconnected(tab);
    }

    @Test
    void retypingTheSameEndpointKeepsCredentials() throws Exception {
        commitEndpoint(ENDPOINT);

        assertTokensHeld();
    }

    @Test
    void editingOnlyThePathKeepsCredentials() throws Exception {
        commitEndpoint("https://mcp.example.com/mcpx");
        commitEndpoint(ENDPOINT);

        assertTokensHeld();
    }

    @Test
    void leavingTheFieldWithAnotherOriginClearsCredentialsAndSaysSo() throws Exception {
        onEdt(() -> {
            endpointField(tab).setText(OTHER_ORIGIN_ENDPOINT);
            loseFocus();
        });

        assertThat(fixture.identities.hasAnyCredential()).isFalse();
        onEdt(() -> assertThat(statusText(tab)).isEqualTo("Endpoint origin changed from https://mcp.example.com to "
                + "https://other.example.test: cleared the credentials of " + alice.name() + ", bob. "
                + "Enter them again in Users."));
    }

    @Test
    void connectingStraightToAnotherOriginNeverSendsTheOldCredentials() throws Exception {
        onEdt(() -> {
            endpointField(tab).setText(OTHER_ORIGIN_ENDPOINT);
            click(connectButton(tab));
        });
        awaitConnectLabel(tab, "Connect");

        ArgumentCaptor<McpServerConfig> configs = ArgumentCaptor.forClass(McpServerConfig.class);
        verify(fixture.clientManager, atLeastOnce()).connect(configs.capture());
        assertThat(configs.getAllValues())
                .filteredOn(config -> config.endpoint().startsWith(OTHER_ORIGIN_ENDPOINT))
                .noneMatch(config -> ALICE_TOKEN.equals(config.auth().headers().get("Authorization")));
        assertThat(fixture.identities.hasAnyCredential()).isFalse();
    }

    @Test
    void tokensEnteredForTheNewEndpointBeforeConnectingAreSentAndKept() throws Exception {
        onEdt(() -> endpointField(tab).setText(OTHER_ORIGIN_ENDPOINT));
        fixture.identities.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-for-other"));
        fixture.identities.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-for-other"));

        onEdt(() -> click(connectButton(tab)));
        awaitConnectLabel(tab, "Disconnect");

        ArgumentCaptor<McpServerConfig> configs = ArgumentCaptor.forClass(McpServerConfig.class);
        verify(fixture.clientManager, atLeastOnce()).connect(configs.capture());
        assertThat(configs.getValue().endpoint()).startsWith(OTHER_ORIGIN_ENDPOINT);
        assertThat(configs.getValue().auth().headers()).containsEntry("Authorization", "Bearer alice-for-other");
        assertTokensHeld();
    }

    private void holdTokens() {
        fixture.identities.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-token"));
        fixture.identities.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-token"));
    }

    private void commitEndpoint(String endpoint) throws Exception {
        onEdt(() -> {
            endpointField(tab).setText(endpoint);
            endpointField(tab).postActionEvent();
        });
        awaitConnectLabel(tab, "Disconnect");
        onEdt(() -> click(connectButton(tab)));
        awaitDisconnected(tab);
    }

    private void loseFocus() {
        var field = endpointField(tab);
        FocusEvent lost = new FocusEvent(field, FocusEvent.FOCUS_LOST);
        for (var listener : field.getFocusListeners()) {
            listener.focusLost(lost);
        }
    }

    private void assertTokensHeld() {
        assertThat(fixture.identities.credential(alice.id())).isPresent();
        assertThat(fixture.identities.credential(bob.id())).isPresent();
    }
}
