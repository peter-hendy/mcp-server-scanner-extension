package com.mcpscanner.ui;

import com.mcpscanner.auth.grants.ClientAuthenticationProviderRegistry;
import com.mcpscanner.auth.grants.EnterpriseIdentityProviderRegistry;
import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.identity.Identity;
import com.mcpscanner.identity.IdentityAuthKind;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.identity.IdentityRole;
import com.mcpscanner.identity.IdentityStore;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.testutil.InMemoryPersistedObject;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GrantFieldsFollowPrimaryTest {

    private final IdentityRegistry registry = new IdentityRegistry(
            new IdentityStore(InMemoryPersistedObject.configStore(), McpEventLog.noop()));
    private final OptionalGrantForms forms = new OptionalGrantForms(new ClientAuthenticationProviderRegistry(List.of()),
            new EnterpriseIdentityProviderRegistry(Map.of()));
    private final OptionalGrantFields clientCredentials = forms.fieldsFor(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS);

    @Test
    void anotherPrimaryUserDoesNotInheritTheFormerPrimarysGrantCredentials() throws Exception {
        GrantFieldsFollowPrimary.watch(registry, forms);
        Identity bob = registry.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        SwingUtilities.invokeAndWait(() -> {
            clientCredentials.issuerField().setText("https://issuer.example");
            clientCredentials.clientIdField().setText("alice-client");
            clientCredentials.secretField().setText("alice-secret");
        });

        registry.makePrimary(bob.id());
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(clientCredentials.clientIdField().getText()).isEmpty();
        assertThat(clientCredentials.secretField().getPassword()).isEmpty();
        assertThat(clientCredentials.issuerField().getText()).isEmpty();
    }

    @Test
    void otherChangesKeepWhatThePrimaryUserEntered() throws Exception {
        GrantFieldsFollowPrimary.watch(registry, forms);
        SwingUtilities.invokeAndWait(() -> clientCredentials.clientIdField().setText("alice-client"));

        registry.add(Identity.create("bob", IdentityRole.PEER, IdentityAuthKind.BEARER));
        registry.update(registry.primaryUser().withName("alice"));
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(clientCredentials.clientIdField().getText()).isEqualTo("alice-client");
    }
}
