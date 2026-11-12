package com.mcpscanner.auth;

import com.mcpscanner.core.connection.CredentialProvider;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class StrategyCredentials implements CredentialProvider {

    private final AuthStrategy strategy;

    public StrategyCredentials(AuthStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy);
    }

    @Override
    public Map<String, String> headers() {
        return Objects.requireNonNullElse(strategy.headers(), Map.of());
    }

    @Override
    public Set<String> headerNames() {
        return strategy.contributedHeaderNames();
    }
}
