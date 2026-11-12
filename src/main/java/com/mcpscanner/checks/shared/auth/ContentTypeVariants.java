package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;

public final class ContentTypeVariants implements AuthVariantGenerator {

    private static final String CONTENT_TYPE = "Content-Type";

    @Override
    public List<AuthVariant> variantsFor(HttpRequest seed) {
        return List.of(
                contentType("json", "application/json"),
                contentType("text-plain", "text/plain"),
                contentType("json-charset", "application/json; charset=utf-8"),
                AuthVariant.uncontrolled(VariantFamily.CONTENT_TYPE, "content-type.missing",
                        new RequestMutation("", request -> request.withRemovedHeader(CONTENT_TYPE))));
    }

    private AuthVariant contentType(String name, String value) {
        return AuthVariant.uncontrolled(VariantFamily.CONTENT_TYPE, "content-type." + name,
                new RequestMutation(value, request -> request.withHeader(CONTENT_TYPE, value)));
    }
}
