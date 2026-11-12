package com.mcpscanner.checks.catalog.oauthrefreshgrant;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.auth.grant.OAuthClient;
import com.mcpscanner.auth.grant.RefreshGrant;
import com.mcpscanner.http.BareRequests;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class RefreshRequestFactory {

    static final String WRONG_SECRET = "mcp-scanner-wrong-client-secret";

    private final URI tokenEndpoint;

    RefreshRequestFactory(URI tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    HttpRequest refresh(RefreshGrant grant, String refreshToken, ClientAuthentication authentication,
                        Optional<Set<String>> scope) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("refresh_token", refreshToken);
        grant.resource().ifPresent(resource -> form.put("resource", resource.toString()));
        scope.ifPresent(scopes -> form.put("scope", String.join(" ", new TreeSet<>(scopes))));
        Optional<String> basicSecret = basicSecret(grant.client(), authentication);
        if (basicSecret.isEmpty()) {
            form.put("client_id", grant.client().clientId());
        }
        HttpRequest request = BareRequests.fromUrl(tokenEndpoint.toString())
                .withMethod("POST")
                .withHeader("Content-Type", "application/x-www-form-urlencoded")
                .withHeader("Accept", "application/json")
                .withBody(encode(form));
        return basicSecret
                .map(secret -> request.withHeader(BasicCredentials.AUTHORIZATION, BasicCredentials.header(grant.client().clientId(), secret)))
                .orElse(request);
    }


    private static Optional<String> basicSecret(OAuthClient client, ClientAuthentication authentication) {
        if (!client.type().isConfidential()) {
            return Optional.empty();
        }
        return switch (authentication) {
            case REGISTERED -> client.secret();
            case OMITTED_SECRET -> Optional.empty();
            case WRONG_SECRET -> Optional.of(WRONG_SECRET);
        };
    }

    private static String encode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(entry -> BasicCredentials.formEncode(entry.getKey()) + "=" + BasicCredentials.formEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

}
