package com.mcpscanner.core.oauth;

public record RegistrationRequest(java.net.URI issuer, RegisteredClient configuredClient, java.net.URI cimdUrl, AuthorizationServerCapabilities metadata, java.net.URI callbackUri, RegistrationMode mode) {}
