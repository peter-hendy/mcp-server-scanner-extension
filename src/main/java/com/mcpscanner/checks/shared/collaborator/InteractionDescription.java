package com.mcpscanner.checks.shared.collaborator;

import burp.api.montoya.collaborator.HttpDetails;
import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Optional;

public final class InteractionDescription {

    private InteractionDescription() {}

    public static String of(Interaction interaction) {
        String origin = " from " + interaction.clientIp().getHostAddress() + " at " + interaction.timeStamp();
        return httpRequestOf(interaction)
                .map(request -> "HTTP request" + origin + ": " + request.method() + " " + request.path())
                .orElseGet(() -> interaction.type() + " interaction" + origin
                        + interaction.dnsDetails().map(dns -> " (" + dns.queryType() + " lookup)").orElse(""));
    }

    public static Optional<HttpRequest> httpRequestOf(Interaction interaction) {
        return interaction.httpDetails().map(HttpDetails::requestResponse)
                .flatMap(exchange -> Optional.ofNullable(exchange.request()));
    }
}
