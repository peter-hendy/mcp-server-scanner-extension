package com.mcpscanner.cvetargets.manifest;

import java.util.List;
import java.util.Optional;

public record EnvironmentToken(String variable) implements TokenSource {

    @Override
    public String token() {
        return Optional.ofNullable(System.getenv(variable))
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("Environment variable " + variable + " is not set"));
    }

    @Override
    public List<String> violations() {
        return variable == null || variable.isBlank() ? List.of("auth.token.variable is required") : List.of();
    }
}
