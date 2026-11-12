package com.mcpscanner.core.oauth;

@FunctionalInterface
public interface OwnedTokenVerifier {
    boolean verify(String accessToken);
}
