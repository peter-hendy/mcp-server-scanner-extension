package com.mcpscanner.cvetargets.manifest;

import java.util.List;

public record StaticToken(String value) implements TokenSource {

    @Override
    public String token() {
        return value;
    }

    @Override
    public List<String> violations() {
        return value == null || value.isBlank() ? List.of("auth.token.value is required") : List.of();
    }
}
