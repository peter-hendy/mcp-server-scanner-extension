package com.mcpscanner.ui;

import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.OAuthAuthCodeStrategy;
import com.mcpscanner.client.McpServerConfig;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static com.mcpscanner.ui.McpScannerTabFixture.ENDPOINT;
import static com.mcpscanner.ui.McpScannerTabFixture.awaitConnectLabel;
import static com.mcpscanner.ui.McpScannerTabFixture.click;
import static com.mcpscanner.ui.McpScannerTabFixture.connectButton;
import static com.mcpscanner.ui.McpScannerTabFixture.emptyInventory;
import static com.mcpscanner.ui.McpScannerTabFixture.endpointField;
import static com.mcpscanner.ui.McpScannerTabFixture.fromEdt;
import static com.mcpscanner.ui.McpScannerTabFixture.onEdt;
import static com.mcpscanner.ui.McpScannerTabFixture.serverConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpScannerTabConnectAsTest {

    private McpScannerTabFixture fixture;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        fixture = new McpScannerTabFixture();
    }

    @Test
    void connectSendsTheConnectAsUsersCredentialsFromIdentities() throws Exception {
        Identity alice = fixture.identities.add(Identity.create("alice", IdentityRole.PEER, IdentityAuthKind.BEARER));
        Identity bob = fixture.identities.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        fixture.identities.setCredential(alice.id(), new BearerTokenAuthStrategy("alice-token"));
        fixture.identities.setCredential(bob.id(), new BearerTokenAuthStrategy("bob-token"));
        when(fixture.clientManager.connect(any())).thenReturn(emptyInventory());
        McpScannerTab tab = fromEdt(fixture::newTab);
        onEdt(() -> fixture.identities.makePrimary(bob.id()));

        onEdt(() -> {
            endpointField(tab).setText(ENDPOINT);
            click(connectButton(tab));
        });
        awaitConnectLabel(tab, "Disconnect");

        ArgumentCaptor<McpServerConfig> config = ArgumentCaptor.forClass(McpServerConfig.class);
        verify(fixture.clientManager).connect(config.capture());
        assertThat(config.getValue().auth().headers()).containsEntry("Authorization", "Bearer bob-token");
        assertThat(fixture.authHolder.get().headers()).containsEntry("Authorization", "Bearer bob-token");
    }

    @Test
    void connectRefusesWhenTheConnectAsUserHasNoTokenYet() throws Exception {
        Identity alice = fixture.identities.add(Identity.create("alice", IdentityRole.PEER, IdentityAuthKind.BEARER));
        fixture.identities.setCredential(fixture.identities.primaryUser().id(),
                new BearerTokenAuthStrategy("user-one-token"));
        McpScannerTab tab = fromEdt(fixture::newTab);
        onEdt(() -> {
            fixture.identities.makePrimary(alice.id());
            endpointField(tab).setText(ENDPOINT);
        });
        await().until(() -> fromEdt(() -> McpScannerTabFixture.header(tab).connectHint().getText())
                .equals("Enter a bearer token for alice"));

        onEdt(() -> click(connectButton(tab)));

        await().until(() -> McpScannerTabFixture.statusText(tab).contains("Enter a bearer token for alice"));
    }

    @Test
    void disconnectKeepsTheConnectAsUsersOAuthSessionAndReconnectReusesIt() throws Exception {
        Identity userOne = fixture.identities.primaryUser();
        fixture.identities.update(userOne.withAuthKind(IdentityAuthKind.OAUTH));
        OAuthAuthCodeStrategy session = mock(OAuthAuthCodeStrategy.class);
        when(session.headers()).thenReturn(Map.of("Authorization", "Bearer session"));
        fixture.identities.setCredential(userOne.id(), session);
        McpScannerTab tab = fromEdt(fixture::newTab);
        when(fixture.clientManager.connect(any())).thenReturn(emptyInventory());
        onEdt(() -> {
            endpointField(tab).setText(ENDPOINT);
            click(connectButton(tab));
        });
        awaitConnectLabel(tab, "Disconnect");

        onEdt(() -> click(connectButton(tab)));
        McpScannerTabFixture.awaitDisconnected(tab);

        assertThat(fixture.identities.credential(userOne.id())).containsSame(session);
        onEdt(() -> click(connectButton(tab)));
        awaitConnectLabel(tab, "Disconnect");
        ArgumentCaptor<McpServerConfig> configs = ArgumentCaptor.forClass(McpServerConfig.class);
        verify(fixture.clientManager, times(2)).connect(configs.capture());
        assertThat(configs.getAllValues()).allSatisfy(config -> assertThat(config.auth()).isSameAs(session));
    }
}
