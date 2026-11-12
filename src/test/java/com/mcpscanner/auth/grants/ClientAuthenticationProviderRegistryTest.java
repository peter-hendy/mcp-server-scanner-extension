package com.mcpscanner.auth.grants;

import com.mcpscanner.core.oauth.ClientCredentialsAuthentication;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ClientAuthenticationProviderRegistryTest {
    @Test
    void duplicateSignerProviderFailureDoesNotExposeProviderDetails() {
        var registration = new ClientAuthenticationProvider() {
            public String id() { return "company"; }
            public ClientCredentialsAuthentication authentication() { return ClientCredentialsAuthentication.basic(); }
            public String toString() { return "signer-secret"; }
        };
        assertThatThrownBy(() -> new ClientAuthenticationProviderRegistry(List.of(registration, registration)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("provider")
                .hasMessageNotContaining("signer-secret");
    }

    @Test
    void unknownSignerAndWrongAuthenticationMethodAreRejected() {
        var registration = new ClientAuthenticationProvider() {
            public String id() { return "company"; }
            public ClientCredentialsAuthentication authentication() { return ClientCredentialsAuthentication.basic(); }
        };
        var registry = new ClientAuthenticationProviderRegistry(List.of(registration));
        assertThatThrownBy(() -> registry.require("missing")).hasMessageContaining("registered");
        assertThatThrownBy(() -> registry.require("company")).hasMessageContaining("private_key_jwt");
    }
}
