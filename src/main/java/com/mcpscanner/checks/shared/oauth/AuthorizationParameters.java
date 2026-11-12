package com.mcpscanner.checks.shared.oauth;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class AuthorizationParameters {

    private AuthorizationParameters() {
    }

    public static Map<String, String> of(AuthorizationServer server, String responseType, String clientId,
                                         URI redirectUri, String state) {
        return of(server, responseType, clientId, redirectUri, state, Pkce.generate());
    }

    public static Map<String, String> of(AuthorizationServer server, String responseType, String clientId,
                                         URI redirectUri, String state, Pkce pkce) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("response_type", responseType);
        parameters.put("client_id", clientId);
        parameters.put("redirect_uri", redirectUri.toString());
        parameters.put("state", state);
        parameters.put("code_challenge_method", "S256");
        parameters.put("code_challenge", pkce.challenge());
        parameters.put("resource", server.resource().toString());
        server.scope().ifPresent(scope -> parameters.put("scope", scope));
        return parameters;
    }

    public static String encode(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public static URI withQuery(URI endpoint, Map<String, String> parameters) {
        String separator = endpoint.getRawQuery() == null ? "?" : "&";
        return URI.create(endpoint + separator + encode(parameters));
    }
}
