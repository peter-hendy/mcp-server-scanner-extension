package com.mcpscanner.ui;

import com.mcpscanner.auth.grants.ClientAuthenticationProviderRegistry;
import com.mcpscanner.auth.grants.OptionalGrantSettings;
import com.mcpscanner.core.oauth.ClientCredentialsAuthentication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class OptionalGrantRules {

    static final String BASIC = "client_secret_basic";
    static final String POST = "client_secret_post";
    static final String PRIVATE_KEY_JWT = "private_key_jwt";
    static final String PUBLIC_CLIENT = "none";

    private final OptionalGrantSettings.Profile profile;
    private final ClientAuthenticationProviderRegistry signers;
    private final List<String> identityProviderIds;

    OptionalGrantRules(OptionalGrantSettings.Profile profile, ClientAuthenticationProviderRegistry signers,
                       List<String> identityProviderIds) {
        this.profile = profile;
        this.signers = signers;
        this.identityProviderIds = List.copyOf(identityProviderIds);
    }

    static boolean usesClientSecret(String method) {
        return BASIC.equals(method) || POST.equals(method);
    }

    static boolean usesSigner(String method) {
        return PRIVATE_KEY_JWT.equals(method);
    }

    boolean isEnterprise() {
        return profile == OptionalGrantSettings.Profile.ENTERPRISE_MANAGED;
    }

    boolean identityProviderMissing() {
        return isEnterprise() && identityProviderIds.isEmpty();
    }

    List<String> authenticationMethods() {
        List<String> methods = new ArrayList<>(List.of(BASIC, POST));
        if (!signers.ids().isEmpty()) {
            methods.add(PRIVATE_KEY_JWT);
        }
        if (isEnterprise()) {
            methods.add(PUBLIC_CLIENT);
        }
        return List.copyOf(methods);
    }

    Optional<String> blocker(OptionalGrantInput input) {
        if (input.clientId().isBlank()) {
            return Optional.of("Configure a client ID for the OAuth grant");
        }
        if (TrustedIssuerUrl.parse(input.issuer()).isEmpty()) {
            return Optional.of("Configure a trusted issuer HTTPS URL (HTTP is allowed only for loopback)");
        }
        if (isEnterprise() && input.identityProviderId().isBlank()) {
            return Optional.of(identityProviderIds.isEmpty()
                    ? "No enterprise identity provider plugin is installed"
                    : "Configure a registered enterprise identity provider ID");
        }
        if (usesClientSecret(input.authenticationMethod()) && !input.hasClientSecret()) {
            return Optional.of("Configure a client secret for " + input.authenticationMethod());
        }
        if (usesSigner(input.authenticationMethod()) && input.signerId().isBlank()) {
            return Optional.of("Install and select a registered private_key_jwt signer provider");
        }
        return Optional.empty();
    }

    OptionalGrantSettings settings(OptionalGrantInput input) {
        blocker(input).ifPresent(reason -> {
            throw new IllegalArgumentException(reason);
        });
        String secret = usesClientSecret(input.authenticationMethod()) ? new String(input.clientSecret()) : null;
        return new OptionalGrantSettings(profile, TrustedIssuerUrl.parse(input.issuer()).orElseThrow(),
                input.clientId().trim(), secret, requestedScopes(input.scopes()),
                clientAuthentication(input), input.identityProviderId());
    }

    private ClientCredentialsAuthentication clientAuthentication(OptionalGrantInput input) {
        return switch (input.authenticationMethod()) {
            case BASIC -> ClientCredentialsAuthentication.basic();
            case POST -> ClientCredentialsAuthentication.post();
            case PUBLIC_CLIENT -> ClientCredentialsAuthentication.publicClient();
            case PRIVATE_KEY_JWT -> signers.require(input.signerId());
            default -> throw new IllegalArgumentException("Select a supported client authentication method");
        };
    }

    private static Set<String> requestedScopes(String scopes) {
        return Arrays.stream(scopes.trim().split("\\s+"))
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
