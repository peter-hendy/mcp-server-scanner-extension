package com.mcpscanner.core.oauth;

@FunctionalInterface
public interface BrowserAuthorization {
    BrowserCallback authorize(AuthorizationRequest request);
}
