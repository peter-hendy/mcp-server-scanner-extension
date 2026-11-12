package com.mcpscanner.core.oauth;

@FunctionalInterface
public interface TokenExchange {
    TokenResponse exchange(TokenRequest request);
}
