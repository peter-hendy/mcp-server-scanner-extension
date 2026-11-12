package com.mcpscanner.ui;

import com.mcpscanner.auth.oauth.OAuthSession;
import com.mcpscanner.config.ExtensionConfigStore;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DcrRegistrationsTest {

    private static final String ISSUER = "https://issuer.example";

    private final ExtensionConfigStore store = mock(ExtensionConfigStore.class);
    private final DcrRegistrations registrations = new DcrRegistrations(store);

    @Test
    void remembersTheManagementTokenAndClientUriOfADynamicRegistration() {
        boolean remembered = registrations.remember(URI.create(ISSUER),
                session(Optional.of("mgmt"), Optional.of("https://issuer.example/clients/1")));

        assertThat(remembered).isTrue();
        verify(store).setDcrRegistrationManagementToken(ISSUER, "mgmt");
        verify(store).setDcrRegistrationClientUri(ISSUER, "https://issuer.example/clients/1");
    }

    @Test
    void aSessionWithoutAManagementTokenIsNotADynamicRegistration() {
        boolean remembered = registrations.remember(URI.create(ISSUER), session(Optional.empty(), Optional.empty()));

        assertThat(remembered).isFalse();
        verify(store, never()).setDcrRegistrationManagementToken(anyString(), any());
    }

    @Test
    void nothingIsRememberedWithoutAnIssuer() {
        assertThat(registrations.remember(null, session(Optional.of("mgmt"), Optional.empty()))).isFalse();
        verify(store, never()).setDcrRegistrationManagementToken(anyString(), any());
    }

    @Test
    void findsARegistrationOnlyWhenItsIssuerHasAManagementToken() {
        when(store.dcrRegistrationManagementToken(ISSUER)).thenReturn("mgmt");
        when(store.dcrRegistrationClientUri(ISSUER)).thenReturn(null);
        when(store.dcrRegistrationManagementToken("https://other.example")).thenReturn(" ");

        assertThat(registrations.forIssuer(" " + ISSUER + " "))
                .contains(new DcrRegistrations.Registration("mgmt", ""));
        assertThat(registrations.forIssuer("https://other.example")).isEmpty();
        assertThat(registrations.forIssuer("")).isEmpty();
    }

    private static OAuthSession session(Optional<String> managementToken, Optional<String> clientUri) {
        return new OAuthSession(null, "client", null, managementToken, clientUri, null);
    }
}
