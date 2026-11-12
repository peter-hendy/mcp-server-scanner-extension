package com.mcpscanner.identity.credentials;

import com.mcpscanner.auth.AccessToken;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.CustomHeaderAuthStrategy;
import com.mcpscanner.auth.HeaderValidator;
import com.mcpscanner.identity.IdentityRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class IdentityCredentials {

    private final IdentityRegistry registry;
    private final SharedAuthSettings shared;
    private final HeaderValidator headerValidator = new HeaderValidator();

    public IdentityCredentials(IdentityRegistry registry, SharedAuthSettings shared) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.shared = Objects.requireNonNull(shared, "shared");
    }

    public CredentialRequirement requirement(String identityId) {
        return CredentialRequirements.of(registry.require(identityId), registry.detected(identityId), shared);
    }

    public void supplyToken(String identityId, String token) {
        if (!(requirement(identityId) instanceof CredentialRequirement.TokenRequired)) {
            throw new IllegalArgumentException("This identity does not use a bearer token");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Enter the bearer token");
        }
        registry.setCredential(identityId, new BearerTokenAuthStrategy(token.strip()));
    }

    public void supplyHeaderValues(String identityId, Map<String, String> values) {
        if (!(requirement(identityId) instanceof CredentialRequirement.HeaderValuesRequired required)) {
            throw new IllegalArgumentException("This identity does not use custom headers");
        }
        registry.setCredential(identityId, new CustomHeaderAuthStrategy(
                headerValidator.validate(valuesFor(required, values))));
    }

    public Optional<String> heldToken(String identityId) {
        return requirement(identityId) instanceof CredentialRequirement.TokenRequired
                ? registry.credential(identityId).flatMap(AuthStrategy::currentToken).map(AccessToken::value)
                : Optional.empty();
    }

    public Map<String, String> heldHeaderValues(String identityId) {
        return requirement(identityId) instanceof CredentialRequirement.HeaderValuesRequired
                ? registry.credential(identityId).map(AuthStrategy::headers).orElse(Map.of())
                : Map.of();
    }

    public void clear(String identityId) {
        registry.removeCredential(identityId);
    }

    private static Map<String, String> valuesFor(CredentialRequirement.HeaderValuesRequired required,
                                                 Map<String, String> values) {
        Map<String, String> ordered = new LinkedHashMap<>();
        for (String name : required.headerNames()) {
            String value = values.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Enter a value for " + name);
            }
            ordered.put(name, value);
        }
        return ordered;
    }
}
