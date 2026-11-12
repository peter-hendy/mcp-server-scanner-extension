package com.mcpscanner.checks.shared.auth;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;

public final class ClientAddressVariants implements AuthVariantGenerator {

    private static final String LOOPBACK = "127.0.0.1";
    private static final String EXTERNAL = "203.0.113.7";

    static List<HeaderProfile> profiles() {
        return List.of(
                new HeaderProfile("forwarded-for", "X-Forwarded-For", LOOPBACK, EXTERNAL),
                new HeaderProfile("forwarded", "Forwarded", "for=" + LOOPBACK, "for=" + EXTERNAL),
                new HeaderProfile("client.x-real-ip", "X-Real-IP", LOOPBACK, EXTERNAL),
                new HeaderProfile("client.client-ip", "Client-IP", LOOPBACK, EXTERNAL));
    }

    @Override
    public List<AuthVariant> variantsFor(HttpRequest seed) {
        return profiles().stream().map(profile -> profile.addedTo(VariantFamily.ROUTING_HEADER)).toList();
    }
}
