package com.mcpscanner.core.oauth;

@FunctionalInterface
public interface ClientAssertionSigner {
    byte[] sign(String algorithm, byte[] signingInput);
}
