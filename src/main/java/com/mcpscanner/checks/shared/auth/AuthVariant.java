package com.mcpscanner.checks.shared.auth;

import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import burp.api.montoya.http.message.requests.HttpRequest;

public record AuthVariant(VariantFamily family, String name, RequestMutation candidate,
                          Optional<RequestMutation> control) {

    public AuthVariant {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(control, "control");
    }

    public static AuthVariant uncontrolled(VariantFamily family, String name, RequestMutation candidate) {
        return new AuthVariant(family, name, candidate, Optional.empty());
    }

    public static AuthVariant controlled(VariantFamily family, String name, RequestMutation candidate,
                                         RequestMutation control) {
        return new AuthVariant(family, name, candidate, Optional.of(control));
    }

    public AuthVariant preparedBy(UnaryOperator<HttpRequest> preparation) {
        return new AuthVariant(family, name, candidate.after(preparation),
                control.map(mutation -> mutation.after(preparation)));
    }
}
