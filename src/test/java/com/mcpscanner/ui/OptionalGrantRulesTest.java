package com.mcpscanner.ui;

import com.mcpscanner.auth.grants.ClientAuthenticationProvider;
import com.mcpscanner.auth.grants.ClientAuthenticationProviderRegistry;
import com.mcpscanner.auth.grants.OptionalGrantSettings.Profile;
import com.mcpscanner.core.oauth.ClientCredentialsAuthentication;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptionalGrantRulesTest {

    private static final ClientCredentialsAuthentication SIGNED =
            ClientCredentialsAuthentication.privateKeyJwt("RS256", "key", (algorithm, input) -> new byte[]{1});
    private static final ClientAuthenticationProviderRegistry NO_SIGNERS =
            new ClientAuthenticationProviderRegistry(List.of());

    @Test
    void machineGrantOffersSecretMethodsOnly() {
        assertThat(rules(Profile.CLIENT_CREDENTIALS, NO_SIGNERS, List.of()).authenticationMethods())
                .containsExactly(OptionalGrantRules.BASIC, OptionalGrantRules.POST);
    }

    @Test
    void installedSignerAndEnterpriseProfileAddMethods() {
        assertThat(rules(Profile.ENTERPRISE_MANAGED, signers(), List.of("idp")).authenticationMethods())
                .containsExactly(OptionalGrantRules.BASIC, OptionalGrantRules.POST,
                        OptionalGrantRules.PRIVATE_KEY_JWT, OptionalGrantRules.PUBLIC_CLIENT);
    }

    @Test
    void onlySecretMethodsUseAClientSecret() {
        assertThat(OptionalGrantRules.usesClientSecret(OptionalGrantRules.BASIC)).isTrue();
        assertThat(OptionalGrantRules.usesClientSecret(OptionalGrantRules.POST)).isTrue();
        assertThat(OptionalGrantRules.usesClientSecret(OptionalGrantRules.PRIVATE_KEY_JWT)).isFalse();
        assertThat(OptionalGrantRules.usesClientSecret(OptionalGrantRules.PUBLIC_CLIENT)).isFalse();
    }

    @Test
    void blockerNamesTheFirstMissingRequirement() {
        var rules = rules(Profile.CLIENT_CREDENTIALS, NO_SIGNERS, List.of());

        assertThat(rules.blocker(input("https://issuer.example", "", "s", OptionalGrantRules.BASIC, "", "")))
                .contains("Configure a client ID for the OAuth grant");
        assertThat(rules.blocker(input("http://issuer.example", "c", "s", OptionalGrantRules.BASIC, "", "")))
                .contains("Configure a trusted issuer HTTPS URL (HTTP is allowed only for loopback)");
        assertThat(rules.blocker(input("https://issuer.example", "c", "", OptionalGrantRules.POST, "", "")))
                .contains("Configure a client secret for client_secret_post");
        assertThat(rules.blocker(input("https://issuer.example", "c", "", OptionalGrantRules.PRIVATE_KEY_JWT, "", "")))
                .contains("Install and select a registered private_key_jwt signer provider");
        assertThat(rules.blocker(input("https://issuer.example", "c", "s", OptionalGrantRules.BASIC, "", "")))
                .isEmpty();
    }

    @Test
    void enterpriseBlockerExplainsAMissingIdentityProvider() {
        var withoutPlugins = rules(Profile.ENTERPRISE_MANAGED, NO_SIGNERS, List.of());
        var withPlugins = rules(Profile.ENTERPRISE_MANAGED, NO_SIGNERS, List.of("a", "b"));
        var publicClient = input("https://issuer.example", "c", "", OptionalGrantRules.PUBLIC_CLIENT, "", "");

        assertThat(withoutPlugins.blocker(publicClient)).contains("No enterprise identity provider plugin is installed");
        assertThat(withPlugins.blocker(publicClient)).contains("Configure a registered enterprise identity provider ID");
    }

    @Test
    void settingsCarryTheSecretOnlyForSecretMethodsAndSplitScopes() {
        var rules = rules(Profile.CLIENT_CREDENTIALS, signers(), List.of());

        var basic = rules.settings(new OptionalGrantInput("https://issuer.example", " machine ",
                "secret".toCharArray(), " read  write ", OptionalGrantRules.BASIC, "", ""));
        var signed = rules.settings(input("https://issuer.example", "c", "ignored",
                OptionalGrantRules.PRIVATE_KEY_JWT, "signer", ""));

        assertThat(basic.clientId()).isEqualTo("machine");
        assertThat(basic.clientSecret()).isEqualTo("secret");
        assertThat(basic.scopes()).containsExactlyInAnyOrder("read", "write");
        assertThat(signed.clientSecret()).isNull();
        assertThat(signed.authentication()).isSameAs(SIGNED);
    }

    @Test
    void settingsRefuseAnIncompleteForm() {
        var rules = rules(Profile.CLIENT_CREDENTIALS, NO_SIGNERS, List.of());

        assertThatThrownBy(() -> rules.settings(input("https://issuer.example", "", "s", OptionalGrantRules.BASIC, "", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Configure a client ID for the OAuth grant");
    }

    private static OptionalGrantRules rules(Profile profile, ClientAuthenticationProviderRegistry signers,
                                            List<String> identityProviders) {
        return new OptionalGrantRules(profile, signers, identityProviders);
    }

    private static OptionalGrantInput input(String issuer, String clientId, String secret, String method,
                                            String signer, String identityProvider) {
        return new OptionalGrantInput(issuer, clientId, secret.toCharArray(), "", method, signer, identityProvider);
    }

    private static ClientAuthenticationProviderRegistry signers() {
        return new ClientAuthenticationProviderRegistry(List.of(new ClientAuthenticationProvider() {
            public String id() { return "signer"; }
            public ClientCredentialsAuthentication authentication() { return SIGNED; }
        }));
    }
}
