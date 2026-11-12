package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;

@FunctionalInterface
public interface AuthVariantGenerator {

    List<AuthVariant> variantsFor(HttpRequest seed);
}
