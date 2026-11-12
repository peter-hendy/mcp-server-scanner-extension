package com.mcpscanner.ui;

import com.mcpscanner.auth.grants.ClientAuthenticationProvider;
import com.mcpscanner.auth.grants.ClientAuthenticationProviderRegistry;
import com.mcpscanner.auth.grants.EnterpriseIdentityProviderRegistry;
import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.core.oauth.ClientCredentialsAuthentication;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.Component;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class OptionalGrantFieldsTest {
    @Test
    void confidentialGrantRequiresExplicitTrustedIssuerBeforeSnapshot() {
        var panel = panel(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS);
        text(panel, "Trusted issuer URL:").setText("");
        text(panel, "Client ID:").setText("client");
        text(panel, "Client secret:").setText("secret");
        assertThatThrownBy(panel::snapshot).hasMessageContaining("issuer");
    }

    @Test
    void enterprisePublicClientUsesExplicitNoneAuthenticationWithoutASecret() {
        var panel = panel(OptionalGrantSettings.Profile.ENTERPRISE_MANAGED);
        text(panel, "Client ID:").setText("public-client");
        ((JComboBox<?>) field(panel, "Client authentication:")).setSelectedItem("none");
        assertThat(panel.snapshot().authentication().method()).isEqualTo("none");
        assertThat(panel.snapshot().clientSecret()).isNull();
    }

    @Test
    void machineSelectionRequiresCredentialsAndSnapshotsWithoutSavingSecret() {
        var panel = panel(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS);
        assertThatThrownBy(panel::snapshot).hasMessageContaining("client ID");
        text(panel, "Client ID:").setText("machine-client");
        assertThatThrownBy(panel::snapshot).hasMessageContaining("client secret");
        text(panel, "Client secret:").setText("secret-value");
        text(panel, "Scopes:").setText("read write read");
        var snapshot = panel.snapshot();
        assertThat(snapshot.clientId()).isEqualTo("machine-client");
        assertThat(snapshot.clientSecret()).isEqualTo("secret-value");
        assertThat(snapshot.scopes()).containsExactlyInAnyOrder("read", "write");
        assertThat(snapshot.toString()).doesNotContain("secret-value");
        panel.clearSecret();
        assertThat(((JPasswordField) field(panel, "Client secret:")).getPassword()).isEmpty();
        assertThatThrownBy(panel::snapshot).hasMessageContaining("client secret");
    }

    @Test
    void enterpriseSelectionRequiresChoosingAmongSeveralIdentityProviders() {
        var panel = mounted(OptionalGrantSettings.Profile.ENTERPRISE_MANAGED,
                new ClientAuthenticationProviderRegistry(List.of()),
                new EnterpriseIdentityProviderRegistry(java.util.Map.of("company", request -> null, "other", request -> null)));
        text(panel, "Trusted issuer URL:").setText("https://issuer.example");
        text(panel, "Client ID:").setText("enterprise-client");
        assertThatThrownBy(panel::snapshot).hasMessageContaining("identity provider");
        text(panel, "Client secret:").setText("secret");
        ((JComboBox<?>) field(panel, "Identity provider ID:")).setSelectedItem("company");
        assertThat(panel.snapshot().enterpriseProviderId()).isEqualTo("company");
        assertThat(panel.snapshot().profile()).isEqualTo(OptionalGrantSettings.Profile.ENTERPRISE_MANAGED);
    }

    @Test
    void privateKeyJwtRequiresSignerAndClearsSecretWhenSelected() {
        var configured = ClientCredentialsAuthentication.privateKeyJwt("RS256", "company-key", (algorithm, input) -> new byte[]{1});
        var provider = new ClientAuthenticationProvider() {
            public String id() { return "company-signer"; }
            public ClientCredentialsAuthentication authentication() { return configured; }
        };
        var panel = mounted(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS,
                new ClientAuthenticationProviderRegistry(List.of(provider)), noIdentityProviders());
        text(panel, "Trusted issuer URL:").setText("https://issuer.example");
        text(panel, "Client ID:").setText("machine-client");
        text(panel, "Client secret:").setText("discard-me");
        ((JComboBox<?>) field(panel, "Client authentication:")).setSelectedItem("private_key_jwt");
        assertThat(((JPasswordField) field(panel, "Client secret:")).getPassword()).isEmpty();
        ((JComboBox<?>) field(panel, "JWT signer provider ID:")).setSelectedItem("");
        assertThatThrownBy(panel::snapshot).hasMessageContaining("signer provider");
        ((JComboBox<?>) field(panel, "JWT signer provider ID:")).setSelectedItem("company-signer");
        assertThat(panel.snapshot().authentication()).isSameAs(configured);
        assertThat(panel.snapshot().clientSecret()).isNull();
    }

    @Test
    void privateKeyJwtIsNotOfferedWithoutAnInstalledSigner() {
        var panel = panel(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS);
        @SuppressWarnings("unchecked")
        var methods = (JComboBox<String>) field(panel, "Client authentication:");
        assertThat(java.util.stream.IntStream.range(0, methods.getItemCount()).mapToObj(methods::getItemAt))
                .containsExactly("client_secret_basic", "client_secret_post");
    }

    @Test
    @SuppressWarnings("unchecked")
    void clientSecretPostRequiresASecretAndSnapshotsWithThePostMethod() {
        var panel = panel(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS);
        text(panel, "Client ID:").setText("machine-client");
        ((JComboBox<String>) field(panel, "Client authentication:")).setSelectedItem("client_secret_post");
        assertThat(panel.blocker()).hasValueSatisfying(reason -> assertThat(reason).contains("client_secret_post"));
        text(panel, "Client secret:").setText("secret-value");

        var snapshot = panel.snapshot();

        assertThat(snapshot.authentication().method()).isEqualTo("client_secret_post");
        assertThat(snapshot.clientSecret()).isEqualTo("secret-value");
    }

    @Test
    void blockerNamesTheFirstMissingRequirementUntilTheFormIsComplete() {
        var panel = panel(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS);
        assertThat(panel.blocker()).hasValueSatisfying(reason -> assertThat(reason).contains("client ID"));
        text(panel, "Client ID:").setText("machine-client");
        assertThat(panel.blocker()).hasValueSatisfying(reason -> assertThat(reason).contains("client secret"));
        text(panel, "Client secret:").setText("secret");
        assertThat(panel.blocker()).isEmpty();
    }

    @Test
    void changeListenerFiresWhenARequiredFieldChanges() {
        var panel = panel(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS);
        var changes = new java.util.concurrent.atomic.AtomicInteger();
        panel.setChangeListener(changes::incrementAndGet);
        text(panel, "Client ID:").setText("machine-client");
        assertThat(changes.get()).isPositive();
    }

    @Test
    void requiredFieldsCarryAMarker() {
        var panel = panel(OptionalGrantSettings.Profile.ENTERPRISE_MANAGED);
        var markers = com.mcpscanner.testutil.SwingTree.visibleDescendants(panel).stream()
                .filter(JLabel.class::isInstance).map(JLabel.class::cast)
                .filter(label -> com.mcpscanner.ui.widgets.LabeledForm.REQUIRED_MARKER.equals(label.getText()))
                .count();
        assertThat(markers).isEqualTo(4);
    }

    @Test
    void enterpriseWithoutAnInstalledProviderWarnsInlineAndBlocksConnect() {
        var panel = mounted(OptionalGrantSettings.Profile.ENTERPRISE_MANAGED,
                new ClientAuthenticationProviderRegistry(List.of()), noIdentityProviders());
        text(panel, "Trusted issuer URL:").setText("https://issuer.example");
        text(panel, "Client ID:").setText("client");
        text(panel, "Client secret:").setText("secret");
        assertThat(com.mcpscanner.testutil.SwingTree.descendants(panel)).anyMatch(component ->
                component instanceof JLabel label && label.getText().contains("plugin is installed"));
        assertThat(panel.blocker()).hasValueSatisfying(reason -> assertThat(reason).contains("plugin"));
    }

    private Mounted panel(OptionalGrantSettings.Profile profile) {
        var panel = mounted(profile, new ClientAuthenticationProviderRegistry(List.of()),
                new EnterpriseIdentityProviderRegistry(java.util.Map.of("company", request -> null)));
        text(panel, "Trusted issuer URL:").setText("https://issuer.example");
        return panel;
    }

    private static Mounted mounted(OptionalGrantSettings.Profile profile, ClientAuthenticationProviderRegistry signers,
                                   EnterpriseIdentityProviderRegistry identityProviders) {
        return new Mounted(new OptionalGrantFields(profile, signers, identityProviders));
    }

    @Test
    void fieldsStayHiddenUntilShownForTheirKind() {
        var fields = new OptionalGrantFields(OptionalGrantSettings.Profile.CLIENT_CREDENTIALS,
                new ClientAuthenticationProviderRegistry(List.of()), noIdentityProviders());
        fields.addTo(new com.mcpscanner.ui.widgets.LabeledForm());

        assertThat(fields.clientIdField().isVisible()).isFalse();

        fields.setShown(true);

        assertThat(fields.clientIdField().isVisible()).isTrue();
    }

    private static final class Mounted extends JPanel {
        private final OptionalGrantFields fields;

        Mounted(OptionalGrantFields fields) {
            this.fields = fields;
            var form = new com.mcpscanner.ui.widgets.LabeledForm();
            fields.addTo(form);
            fields.setShown(true);
            add(form);
        }

        OptionalGrantSettings snapshot() {
            return fields.snapshot();
        }

        java.util.Optional<String> blocker() {
            return fields.blocker();
        }

        void clearSecret() {
            fields.clearSecret();
        }

        void setChangeListener(Runnable listener) {
            fields.setChangeListener(listener);
        }
    }

    private static EnterpriseIdentityProviderRegistry noIdentityProviders() {
        return new EnterpriseIdentityProviderRegistry(java.util.Map.of());
    }

    private JTextField text(JPanel panel, String label) { return (JTextField) field(panel, label); }

    private Component field(JPanel panel, String label) {
        return com.mcpscanner.testutil.SwingTree.labelledField(panel, label)
                .orElseThrow(() -> new AssertionError("Missing field: " + label));
    }
}
