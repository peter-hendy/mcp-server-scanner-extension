package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;

public final class AnonymousVariants implements AuthVariantGenerator {

    private final AuthVariantGenerator generator;
    private final CredentialStripper stripper;

    public AnonymousVariants(AuthVariantGenerator generator, CredentialStripper stripper) {
        this.generator = generator;
        this.stripper = stripper;
    }

    @Override
    public List<AuthVariant> variantsFor(HttpRequest seed) {
        return generator.variantsFor(stripper.apply(seed)).stream()
                .map(variant -> variant.preparedBy(stripper))
                .toList();
    }
}
