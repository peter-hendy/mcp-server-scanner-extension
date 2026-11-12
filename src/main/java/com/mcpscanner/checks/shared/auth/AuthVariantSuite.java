package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.AuthStrategy;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class AuthVariantSuite {

    private final CredentialStripper stripper;
    private final CredentialVariants credentials;
    private final Map<VariantFamily, AuthVariantGenerator> generators;

    public AuthVariantSuite(Supplier<AuthStrategy> authStrategy, String canary) {
        this.stripper = new CredentialStripper(authStrategy);
        this.credentials = new CredentialVariants(authStrategy, canary);
        this.generators = new EnumMap<>(Map.of(
                VariantFamily.CREDENTIAL, credentials,
                VariantFamily.CONTENT_TYPE, new ContentTypeVariants(),
                VariantFamily.JSON_BODY, new JsonBodyVariants(),
                VariantFamily.PATH, new PathVariants(canary),
                VariantFamily.ROUTING_HEADER, new RoutingHeaderVariants(canary),
                VariantFamily.HOST, new HostVariants(),
                VariantFamily.SESSION, new SessionVariants(canary)));
    }

    public List<AuthVariant> variantsFor(HttpRequest authenticatedSeed) {
        return variantsFor(authenticatedSeed, EnumSet.allOf(VariantFamily.class));
    }

    public List<AuthVariant> variantsFor(HttpRequest authenticatedSeed, Set<VariantFamily> families) {
        return families.stream()
                .sorted()
                .map(generators::get)
                .flatMap(generator -> anonymous(generator).variantsFor(authenticatedSeed).stream())
                .toList();
    }

    public AuthVariant noCredentialControl() {
        return stripped(credentials.noCredentials());
    }

    public AuthVariant malformedCredentialControl() {
        return stripped(credentials.malformedCredential());
    }

    public AuthVariant fabricatedCredential() {
        return stripped(credentials.fabricatedCredential());
    }

    private AuthVariant stripped(AuthVariant variant) {
        return variant.preparedBy(stripper);
    }

    private AuthVariantGenerator anonymous(AuthVariantGenerator generator) {
        return new AnonymousVariants(generator, stripper);
    }
}
