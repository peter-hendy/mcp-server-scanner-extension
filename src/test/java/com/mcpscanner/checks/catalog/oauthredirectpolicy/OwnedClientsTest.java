package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import com.mcpscanner.checks.catalog.oauthredirectpolicy.FakeAuthorizationServer.RegistrationReply;
import com.mcpscanner.testutil.MontoyaTestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OwnedClientsTest {

    private FakeAuthorizationServer server;

    @BeforeEach
    void setUp() {
        MontoyaTestFactory.install();
        server = new FakeAuthorizationServer();
    }

    private OwnedClients clients() {
        return new OwnedClients(server.registrar());
    }

    @Test
    void loopbackClientOwnsARegisteredLoopbackCallback() {
        OwnedClient loopback = clients().loopback().orElseThrow();

        assertThat(loopback.callback().toString()).startsWith("http://127.0.0.1:49151/scancheck-registered-");
        assertThat(FakeAuthorizationServer.registeredRedirectUris(server.registrations().get(0)))
                .containsExactly(loopback.callback().toString());
        assertThat(loopback.clientId()).isEqualTo(loopback.registration().clientId());
    }

    @Test
    void httpsClientOwnsARegisteredHttpsControlCallback() {
        OwnedClient https = clients().https().orElseThrow();

        assertThat(https.callback().toString()).startsWith("https://client.example.com/scancheck-control-");
        assertThat(FakeAuthorizationServer.registeredRedirectUris(server.registrations().get(0)))
                .containsExactly(https.callback().toString());
    }

    @Test
    void loopbackRegistersOnlyOnce() {
        OwnedClients clients = clients();

        Optional<OwnedClient> first = clients.loopback();
        Optional<OwnedClient> second = clients.loopback();

        assertThat(second).isSameAs(first);
        assertThat(server.registrations()).hasSize(1);
    }

    @Test
    void httpsRegistersOnlyOnce() {
        OwnedClients clients = clients();

        clients.https();
        clients.https();

        assertThat(server.registrations()).hasSize(1);
    }

    @Test
    void loopbackAndHttpsAreSeparateClients() {
        OwnedClients clients = clients();

        assertThat(clients.loopback().orElseThrow().clientId())
                .isNotEqualTo(clients.https().orElseThrow().clientId());
        assertThat(server.registrations()).hasSize(2);
    }

    @Test
    void registrationThatDoesNotEchoTheCallbackOwnsNoClient() {
        server.registering(uris -> RegistrationReply.OMIT_REDIRECT_URIS);

        assertThat(clients().loopback()).isEmpty();
    }

    @Test
    void rejectedRegistrationOwnsNoClientAndIsNotRetried() {
        server.registering(uris -> RegistrationReply.REJECT);
        OwnedClients clients = clients();

        assertThat(clients.https()).isEmpty();
        assertThat(clients.https()).isEmpty();
        assertThat(server.registrations()).hasSize(1);
    }
}
