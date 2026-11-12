package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.shared.auth.AuthVariantSuite;
import com.mcpscanner.checks.shared.oauth.HttpUris;
import com.mcpscanner.checks.shared.oauth.JsonDocument;
import com.mcpscanner.checks.shared.oauth.ResponseHeaders;
import com.mcpscanner.checks.shared.oauth.WellKnownLocations;
import com.mcpscanner.checks.shared.probe.ProbeChannel;
import com.mcpscanner.http.BareRequests;

import java.net.URI;
import java.util.Optional;

final class EnforcementProbe {

    private static final String PING = "{\"jsonrpc\":\"2.0\",\"id\":\"mcp-scanner-enforcement\",\"method\":\"ping\"}";
    private static final int UNAUTHORIZED = 401;
    private static final String WWW_AUTHENTICATE = "WWW-Authenticate";

    private final ProbeChannel channel;
    private final AuthVariantSuite suite;

    EnforcementProbe(ProbeChannel channel, AuthVariantSuite suite) {
        this.channel = channel;
        this.suite = suite;
    }

    Optional<HttpRequestResponse> observe(AuditSubject subject) {
        HttpRequestResponse ping = channel.send(suite.noCredentialControl().candidate().applyTo(subject.seed())
                .withBody(PING));
        if (challenged(ping)) {
            return Optional.of(ping);
        }
        return HttpUris.parse(subject.seed().url()).flatMap(this::protectedResourceMetadata);
    }

    private Optional<HttpRequestResponse> protectedResourceMetadata(URI endpoint) {
        return WellKnownLocations.protectedResourceMetadata(endpoint).stream()
                .map(location -> channel.send(BareRequests.fromUrl(location.toString())
                        .withAddedHeader("Accept", "application/json")))
                .filter(EnforcementProbe::advertisesAuthorizationServers)
                .findFirst();
    }

    private static boolean challenged(HttpRequestResponse exchange) {
        return exchange != null && exchange.response() != null
                && (exchange.response().statusCode() == UNAUTHORIZED
                || ResponseHeaders.first(exchange.response(), WWW_AUTHENTICATE).isPresent());
    }

    private static boolean advertisesAuthorizationServers(HttpRequestResponse exchange) {
        return exchange != null && JsonDocument.fromSuccessfulResponse(exchange)
                .map(document -> !document.texts("authorization_servers").isEmpty())
                .orElse(false);
    }
}
